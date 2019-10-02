package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.doclib.handlers.UnarchiveHandler
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.util.MongoCodecs
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.{Document, MongoCollection}

import scala.concurrent.ExecutionContextExecutor

/**
  * RabbitMQ Consumer to unarchive files
  */
object ConsumerUnarchive extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-unarchive")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** Initialise Mongo **/
  implicit val codecs: CodecRegistry = MongoCodecs.get
  implicit val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))

  /** initialise queues **/
  val archiver: Queue[ArchiveMsg] = new Queue[ArchiveMsg](config.getString("doclib.archive.queue"), Some("unarchiver"))
  val supervisor: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("doclib.supervisor.queue"), Some("unarchiver"))
  val prefetch: Queue[PrefetchMsg] = new Queue[PrefetchMsg](config.getString("downstream.queue"), Some("unarchiver"))
  val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"), Some("unarchiver"))

  val subscription: SubscriptionRef = upstream.subscribe(new UnarchiveHandler(prefetch, archiver, supervisor).handle, config.getInt("upstream.concurrent"))

}
