package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{ArchiveMsg, DoclibMsg, PrefetchMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.metadata.MetaString
import io.mdcatapult.doclib.models.{Derivative, DoclibDoc, Origin}
import io.mdcatapult.doclib.util.DoclibFlags
import io.mdcatapult.klein.queue.{Queue, Sendable}
import io.mdcatapult.unarchive.extractors.{Auto, Gzip, SevenZip}
import org.apache.commons.compress.archivers.ArchiveException
import org.bson.BsonDocument
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonArray, BsonValue}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{Document, MongoCollection}
import play.api.libs.json.{JsSuccess, Json}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class UnarchiveHandler(prefetch: Sendable[PrefetchMsg], archiver: Sendable[ArchiveMsg], supervisor: Sendable[SupervisorMsg])
                      (implicit ac: ActorSystem,
                       ex: ExecutionContextExecutor,
                       config: Config,
                       collection: MongoCollection[DoclibDoc]
                      ) extends LazyLogging {

  lazy val flags = new DoclibFlags(config.getString("doclib.flag"))


  def enqueue(extracted: List[String], doc: DoclibDoc): Future[Option[Boolean]] = {
    extracted.foreach(path ⇒ {
      prefetch.send(PrefetchMsg(
        source = path,
        origin = Some(List(Origin(
          scheme = "mongodb",
          metadata = Some(List(
            MetaString("db", config.getString("mongo.database")),
            MetaString("collection", config.getString("mongo.collection")),
            MetaString("_id", doc._id.toHexString)))
        ))),
        tags = doc.tags,
        metadata = doc.metadata,
        derivative =  Some(true)
      ))
    })
    Future.successful(Some(true))
  }


  /**
    * persists all unarchived
    * @param doc
    * @param unarchived
    * @return
    */
  def persist(doc: DoclibDoc, unarchived: List[String]): Future[List[Derivative]] =
    collection.updateOne(equal("_id", doc._id),
      addEachToSet("derivatives", unarchived.map(path ⇒ Derivative("unarchived", path)):_*),
    ).toFutureOption().map(_ ⇒ doc.derivatives.getOrElse(List())
      .filter(d ⇒ d.`type` == "unarchived" && !unarchived.contains(d.path)))


  /**
    *
    * @param doc
    * @param archivable
    * @return
    */
  def archive(doc: DoclibDoc, archivable: List[Derivative]): Future[Option[Any]] =
    if (archivable.nonEmpty) {
      collection.updateOne(equal("_id", doc._id),
        pullByFilter(
          equal("derivatives", or(
            archivable.map(d ⇒
              and(
                equal("type", "unarchived"),
                equal("path", d.path)
              )
            ): _*)))
      ).toFutureOption().andThen({
        case Success(_) => archivable.foreach(d =>
          archiver.send(ArchiveMsg(source = Some(d.path))))
      })
    } else {
      Future.successful(Some(true))
    }

  def unarchive(document: DoclibDoc): Option[List[String]] =
    Try(document.mimetype match {
      // try as compressed archive, else try as compressed file
      case "application/gzip" ⇒ Try(new Auto(document.source).extract) match {
        case Success(r) => r
        case Failure(_: ArchiveException) ⇒ new Gzip(document.source).extract
        case Failure(e) ⇒ throw e
      }
      case "application/x-7z-compressed" ⇒ new SevenZip(document.source).extract
      case _ ⇒ new Auto(document.source).extract
    }) match {
      case Success(result) ⇒ Some(result)
      case Failure(exception) ⇒ throw exception
    }


  def fetch(id: String): Future[Option[DoclibDoc]] = collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] =
    (for {
      doc: DoclibDoc ← OptionT(fetch(msg.id))
      started: UpdateResult ← OptionT(flags.start(doc))
      unarchived ← OptionT.fromOption[Future](unarchive(doc))
      archivable ← OptionT.liftF(persist(doc, unarchived))
      result ← OptionT(archive(doc, archivable))
      _ ← OptionT(enqueue(unarchived, doc))
      _ ← OptionT(flags.end(doc, started.getModifiedCount > 0))

    } yield (unarchived, doc)).value.andThen({
      case Success(result) ⇒ result match {
        case Some(r) ⇒
          supervisor.send(SupervisorMsg(id = r._2._id.toHexString))
          logger.info(f"COMPLETE: ${msg.id} - Unarchived ${r._1.length}")
        case None ⇒ throw new Exception("Unidentified error occurred")
      }
      case Failure(_) ⇒
        Try(Await.result(fetch(msg.id), Duration.Inf)) match {
          case Success(value: Option[DoclibDoc]) ⇒ value match {
            case Some(doc) ⇒ flags.error(doc, noCheck = true)
            case _ ⇒ // do nothing as error handling will capture
          }
          case Failure(_) ⇒ // do nothing as error handling will capture
        }
    })

}
