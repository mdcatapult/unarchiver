package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.doclib.handlers.UnarchiveHandler
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
  implicit val collection: MongoCollection[Document] = mongo.collection

  /** initialise queues **/
  val downstream: Queue[PrefetchMsg] = new Queue[PrefetchMsg](config.getString("downstream.queue"), Option(config.getString("op-rabbit.topic-exchange-name")))
  val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"), Option(config.getString("op-rabbit.topic-exchange-name")))
  val subscription: SubscriptionRef = upstream.subscribe(new UnarchiveHandler(downstream).handle, config.getInt("upstream.concurrent"))

}
