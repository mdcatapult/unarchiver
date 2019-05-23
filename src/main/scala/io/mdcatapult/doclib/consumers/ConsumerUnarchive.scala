package io.mdcatapult.doclib.consumers

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{IncomingMsg, PrefetchMsg}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.bson.types.ObjectId
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

/**
  * RabbitMQ Consumer to unarchive files
  */
object ConsumerUnarchive extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-unarchive")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** initialise queues **/
  val upstream: Queue[IncomingMsg] = new Queue[IncomingMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(handle, config.getInt("upstream.concurrent"))
  val downstream: Queue[PrefetchMsg] = new Queue[PrefetchMsg](config.getString("downstream.queue"))

  /** Initialise Mongo **/
  val mongo = new Mongo()
  val collection = mongo.collection


  def enqueue(extracted: List[String]): Unit = {
    extracted.foreach(p ⇒ {
      downstream.send(PrefetchMsg(source=p))
    })
  }

  def persist(doc: Document, unarchived: List[String]): Future[Option[Any]] = {
    val id = doc.getObjectId("_id")
    val query = equal("_id", id)
    collection.updateOne(query, and(
      set(s"unarchived", unarchived),
      set(config.getString("upstream.queue"), true)
    )).toFutureOption().andThen({
      case Failure(t) ⇒ logger.error(f"Mongo Update failed: ${id.toString}", t)
      case Success(_) ⇒
        logger.info(f"COMPLETE: $id - EXTRACTED ${unarchived.length} → ${doc.getString("source")}")
        enqueue(unarchived)
    })
  }

  def extract( source: String ): List[String] = {
    val targetRoot = config.getString("extract.to.path").replaceAll("/+$", "")
    val sourceName = FilenameUtils.removeExtension(FilenameUtils.getBaseName(source))
    val targetPath = s"$targetRoot/$sourceName/"
    val ais = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(new FileInputStream(source)))
    Iterator.continually(ais.getNextEntry)
      .takeWhile(ais.canReadEntryData)
      .filterNot(_.isDirectory)
      .map(entry ⇒ {
        val target = new File(s"$targetPath/${entry.getName}")
        target.getParentFile.mkdirs()
        val ois = new FileOutputStream(target)
        IOUtils.copy(ais, ois)
        target.getPath
      }).toList
  }

  def unarchive(document: Document) = {
    Try(extract(document.getString("source"))) match {
      case Success(result) ⇒ Some(result)
      case Failure(exception) ⇒ throw exception
    }
  }

  def fetch(id: String): Future[Option[Document]] = collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

  def handle(msg: IncomingMsg, key: String): Future[Option[Any]] =
    (for {
      doc ← OptionT(fetch(msg.id))
      if doc.contains("source")
      unarchived ← OptionT.fromOption[Future](unarchive(doc))
      persisted ← OptionT(persist(doc, unarchived))
    } yield persisted).value
}
