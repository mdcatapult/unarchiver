/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.doclib.consumers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.{AnyHandlerResult, UnarchiveHandler, UnarchiveHandlerResult}
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.{AppConfig, DoclibDoc, ParentChildMapping}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.util.admin.{Server => AdminServer}
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
import org.mongodb.scala.MongoCollection

import scala.util.Try

/**
  * RabbitMQ Consumer to unarchive files
  */
object ConsumerUnarchive extends AbstractConsumer[SupervisorMsg, UnarchiveHandlerResult]() {

  override def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo) = {
    import as.dispatcher

    AdminServer(config).start()

    implicit val appConfig: AppConfig =
      AppConfig(
        config.getString("consumer.name"),
        config.getInt("consumer.concurrency"),
        config.getString("consumer.queue"),
        Try(config.getString("consumer.exchange")).toOption
      )

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.documents-collection"))
    implicit val derivativesCollection: MongoCollection[ParentChildMapping] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.derivative-collection"))

    // Note that the AnyHandlerResult is because the Queue expects the type of response from the "business logic"
    // In reality we don't care here because we are just sending and not subscribing
    val supervisor: Queue[SupervisorMsg, AnyHandlerResult] = Queue[SupervisorMsg, AnyHandlerResult]("doclib.supervisor.queue")
    val downstream: Queue[PrefetchMsg, AnyHandlerResult] = Queue[PrefetchMsg, AnyHandlerResult]("downstream.queue")
    val upstream: Queue[DoclibMsg, UnarchiveHandlerResult] = Queue[DoclibMsg, UnarchiveHandlerResult]("consumer.queue")

    val readLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.read-limit"))
    val writeLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.write-limit"))

    upstream.subscribe(
      new UnarchiveHandler(
        downstream,
        supervisor,
        readLimiter,
        writeLimiter
      ).handle,
      config.getInt("consumer.concurrency")
    )
  }
}
