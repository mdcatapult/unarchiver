package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{legacy, _}
import io.mdcatapult.doclib.models.PrefetchOrigin
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.bson.types.ObjectId
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{BsonBoolean, BsonNull, BsonValue}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.result.UpdateResult

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import io.mdcatapult.unarchive.extractors.{Auto, SevenZip}

/**
  * RabbitMQ Consumer to unarchive files
  */
object ConsumerUnarchive extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-unarchive")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** initialise queues **/
  val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(handle, config.getInt("upstream.concurrent"))


  /** Initialise Mongo **/
  val mongo = new Mongo()
  val collection = mongo.collection

  def enqueueLegacy(extracted: List[String], doc: Document): Unit = {
    val downstream: Queue[legacy.PrefetchMsg] = new Queue[legacy.PrefetchMsg](config.getString("downstream.queue"))
    extracted.foreach(p ⇒ {
      downstream.send(legacy.PrefetchMsg(
        source = p,
        origin = doc.getObjectId("_id").toString,
        tags = Some(doc.getList("tags", classOf[String]).asScala.toList),
        metadata = Some(doc.getEmbedded(List("metadata").asJava, Map[String, Any]()))
      ))
    })
  }

  def enqueue(extracted: List[String], doc: Document): Unit = {
     val downstream: Queue[PrefetchMsg] =new Queue[PrefetchMsg](config.getString("downstream.queue"))
     extracted.foreach(p ⇒ {
       downstream.send(PrefetchMsg(
         source = p,
         origin = Some(List[PrefetchOrigin]()),
         tags = Some(doc.getList("tags", classOf[String]).asScala.toList),
         metadata = Some(doc.getEmbedded(List("metadata").asJava, Map[String, Any]()))
       ))
     })
  }

  def persist(doc: Document, unarchived: List[String]): Future[Option[UpdateResult]] = {
    val id = doc.getObjectId("_id")
    val query = equal("_id", id)
    collection.updateOne(query, and(
      set(config.getString("unarchive.targetProperty"), unarchived),
      set(config.getString("doclib.flag"), true)
    )).toFutureOption().andThen({
      case Failure(t) ⇒ throw t
      case Success(_) ⇒
        if (config.getBoolean("doclib.legacy")) enqueue(unarchived, doc)
        else enqueueLegacy(unarchived, doc)
    })
  }


  def unarchive(document: Document) =
    Try(document.getString("mimetype") match {
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
    } yield unarchived).value.andThen({
      case Success(result) ⇒ result match {
        case Some(unarchived) ⇒ logger.info(f"COMPLETE: ${msg.id} - Unarchived ${unarchived.length}")
        case None ⇒ setFlag(msg.id, BsonBoolean(false)).andThen({
          case Failure(err) ⇒ throw err
          case _ ⇒ logger.debug(f"DROPPING MESSAGE: ${msg.id}")
        })
      }
      case Failure(err) ⇒ throw err

    })
}
