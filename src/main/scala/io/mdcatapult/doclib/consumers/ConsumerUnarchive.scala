package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.doclib.handlers.UnarchiveHandler
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
  implicit val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[Document] = mongo.collection

  /** initialise queues **/
  val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(new UnarchiveHandler().handle, config.getInt("upstream.concurrent"))

}
