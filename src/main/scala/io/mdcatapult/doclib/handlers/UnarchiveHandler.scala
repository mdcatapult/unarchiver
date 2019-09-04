package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{DoclibMsg, PrefetchMsg, legacy}
import io.mdcatapult.doclib.models.PrefetchOrigin
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.unarchive.extractors.{Auto, Gzip, SevenZip}
import org.bson.types.ObjectId
import org.mongodb.scala.{Document, MongoCollection}
import org.mongodb.scala.bson.{BsonBoolean, BsonDocument, BsonNull, BsonValue}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.result.UpdateResult
import org.apache.commons.compress.archivers.ArchiveException

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class UnarchiveHandler(downstream: Queue[PrefetchMsg])(implicit ac: ActorSystem, ex: ExecutionContextExecutor, config: Config, collection: MongoCollection[Document]) extends LazyLogging {

  def enqueue(extracted: List[String], doc: Document): Future[Option[Boolean]] = {
    val mdata = if (doc.contains("metadata")) doc("metadata").asDocument() else BsonDocument()
    extracted.foreach(p ⇒ {
      downstream.send(PrefetchMsg(
        source = p,
        origin=Some(List(PrefetchOrigin(
          scheme = "mongodb",
          metadata = Some(Map[String, Any](
            "db" → config.getString("mongo.database"),
            "collection" → config.getString("mongo.collection"),
            "_id" → doc.getObjectId("_id").toString))))),
        tags = Some(doc.getList("tags", classOf[String]).asScala.toList),
        metadata = Some(mdata)
      ))
    })
    Future.successful(Some(true))
  }


  def persist(doc: Document, unarchived: List[String]): Future[Option[UpdateResult]] = {
    val id = doc.getObjectId("_id")
    val query = equal("_id", id)
    collection.updateOne(query, and(
      set(config.getString("unarchive.targetProperty"), unarchived),
      set(config.getString("doclib.flag"), true)
    )).toFutureOption()
  }


  def unarchive(document: Document): Option[List[String]] =
    Try(document.getString("mimetype") match {
      // try as compressed archive, else try as compressed file
      case "application/gzip" ⇒ Try(new Auto(document.getString("source")).extract) match {
        case Success(r) => r
        case Failure(_: ArchiveException) ⇒ new Gzip(document.getString("source")).extract
        case Failure(e) ⇒ throw e
      }
      case "application/x-7z-compressed" ⇒ new SevenZip(document.getString("source")).extract
      case _ ⇒ new Auto(document.getString("source")).extract
    }) match {
      case Success(result) ⇒ Some(result)
      case Failure(exception) ⇒ throw exception
    }


  def fetch(id: String): Future[Option[Document]] = collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

  /**
    * set processing flags on read document
    * @param id String
    * @param v BsonValue (null/boolean
    * @return
    */
  def setFlag(id: String, v: BsonValue): Future[Option[UpdateResult]] = {
    collection.updateOne(
      equal("_id", new ObjectId(id)),
      set(config.getString("doclib.flag"), v)
    ).toFutureOption()
  }

  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] =
    (for {
      doc ← OptionT(fetch(msg.id))
      if doc.contains("source")
      _ ← OptionT(setFlag(msg.id, BsonNull()))
      unarchived ← OptionT.fromOption[Future](unarchive(doc))
      persisted ← OptionT(persist(doc, unarchived))
      _ ← OptionT(enqueue(unarchived, doc))
    } yield (unarchived, persisted)).value.andThen({
      case Success(result) ⇒ result match {
        case Some(r) ⇒
          logger.info(f"COMPLETE: ${msg.id} - Unarchived ${r._1.length}")
        case None ⇒ setFlag(msg.id, BsonBoolean(false)).andThen({
          case Failure(err) ⇒ throw err
          case _ ⇒ logger.debug(f"DROPPING MESSAGE: ${msg.id}")
        })
      }
      case Failure(err) ⇒ throw err

    })

}
