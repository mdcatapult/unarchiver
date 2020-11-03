package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.UnarchiveHandler
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.{DoclibDoc, ParentChildMapping}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.{Envelope, Queue}
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
import org.mongodb.scala.MongoCollection
import play.api.libs.json.Format

/**
  * RabbitMQ Consumer to unarchive files
  */
object ConsumerUnarchive extends AbstractConsumer("consumer-unarchive") {

  override def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.database.getCollection(config.getString("mongo.collection"))
    implicit val derivativesCollection: MongoCollection[ParentChildMapping] =
      mongo.database.getCollection(config.getString("mongo.derivative_collection"))

    def queue[T <: Envelope](property: String)(implicit f: Format[T]): Queue[T] =
      Queue[T](config.getString(property), consumerName = Some("unarchiver"))

    val archiver: Queue[ArchiveMsg] = queue("doclib.archive.queue")
    val supervisor: Queue[SupervisorMsg] = queue("doclib.supervisor.queue")
    val prefetch: Queue[PrefetchMsg] = queue("downstream.queue")
    val upstream: Queue[DoclibMsg] = queue("upstream.queue")

    val readLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.limit.read"))
    val writeLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.limit.write"))

    upstream.subscribe(
      new UnarchiveHandler(
        prefetch,
        archiver,
        supervisor,
        readLimiter,
        writeLimiter,
      ).handle,
      config.getInt("upstream.concurrent")
    )
  }
}
