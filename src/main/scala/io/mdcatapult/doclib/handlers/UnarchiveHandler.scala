package io.mdcatapult.doclib.handlers

import java.util.UUID

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.flag.{FlagContext, MongoFlagStore}
import io.mdcatapult.doclib.messages.{ArchiveMsg, DoclibMsg, PrefetchMsg, SupervisorMsg}
import io.mdcatapult.doclib.metrics.Metrics.handlerCount
import io.mdcatapult.doclib.models._
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.unarchive.extractors.{Auto, Gzip, SevenZip}
import io.mdcatapult.util.concurrency.LimitedExecution
import io.mdcatapult.util.models.Version
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.apache.commons.compress.archivers.ArchiveException
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.InsertManyResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class UnarchiveHandler(
                        prefetch: Sendable[PrefetchMsg],
                        archiver: Sendable[ArchiveMsg],
                        supervisor: Sendable[SupervisorMsg],
                        readLimit: LimitedExecution,
                        writeLimit: LimitedExecution,
                      )
                      (implicit ec: ExecutionContext,
                       config: Config,
                       collection: MongoCollection[DoclibDoc],
                       derivativesCollection: MongoCollection[ParentChildMapping]
                      ) extends LazyLogging {

  private val consumerName = "consumer-unarchive"
  private val docExtractor = DoclibDocExtractor()
  private val version = Version.fromConfig(config)
  private val flags = new MongoFlagStore(version, docExtractor, collection, nowUtc)

  def enqueue(extracted: List[String], doc: DoclibDoc): Future[Option[Boolean]] = {
    // Let prefetch know that it is an unarchived derivative
    val derivativeMetadata = List[MetaValueUntyped](MetaString("derivative.type", "unarchived"))
    extracted.foreach(path => {
      prefetch.send(PrefetchMsg(
        source = path,
        origins = Some(List(Origin(
          scheme = "mongodb",
          metadata = Some(List(
            MetaString("db", config.getString("mongo.database")),
            MetaString("collection", config.getString("mongo.collection")),
            MetaString("_id", doc._id.toHexString)))
        ))),
        tags = doc.tags,
        metadata = Some(doc.metadata.getOrElse(Nil) ::: derivativeMetadata),
        derivative =  Some(true)
      ))
    })
    Future.successful(Some(true))
  }

  def persist(doc: DoclibDoc, unarchived: List[String]): Future[Option[InsertManyResult]] =
    derivativesCollection.insertMany(createDerivativesFromPaths(doc, unarchived)).toFutureOption()

  def archive(doc: DoclibDoc, archivable: List[Derivative]): Future[Option[Any]] =
    if (archivable.nonEmpty) {
      writeLimit(collection, "archive doc") {
        _.updateOne(equal("_id", doc._id),
          pullByFilter(
            equal("derivatives", or(
              archivable.map(d =>
                and(
                  equal("type", "unarchived"),
                  equal("path", d.path)
                )
              ): _*)))
        ).toFutureOption().andThen({
          case Success(_) => archivable.foreach(d =>
            archiver.send(ArchiveMsg(source = Some(d.path))))
        })
      }
    } else {
      Future.successful(Some(true))
    }

  def unarchive(document: DoclibDoc): Option[List[String]] = {
    val sourcePath = document.source

    Try(document.mimetype match {
      // try as compressed archive, else try as compressed file
      case "application/gzip" =>
        Try(new Auto(sourcePath).extract()) match {
          case Success(r) => r
          case Failure(_: ArchiveException) => new Gzip(sourcePath).extract()
          case Failure(e) => throw e
        }
      case "application/x-7z-compressed" =>
        new SevenZip(sourcePath).extract()
      case _ =>
        new Auto(sourcePath).extract()
    }) match {
      case Success(result) => Some(result)
      case Failure(exception) => throw exception
    }
  }

  def fetch(id: String): Future[Option[DoclibDoc]] =
    readLimit(collection, "fetch document by id") {
      _.find(equal("_id", new ObjectId(id)))
        .first()
        .toFutureOption()
    }

  /**
    * Create list of parent child mappings
    * @param doc DoclibDoc
    * @param paths List[String]
    * @return List[Derivative] unique list of derivatives
    */
  def createDerivativesFromPaths(doc: DoclibDoc, paths: List[String]): List[ParentChildMapping] =
  //TODO This same pattern is used in other consumers so maybe we can move to a shared lib in common or a shared consumer lib.
    paths.map(d => ParentChildMapping(_id = UUID.randomUUID(), childPath = d, parent = doc._id, consumer = Some("unarchived")))

  /** Handler for the unarchive consumer.
    * @param msg message to process
    * @param key routing key from rabbitmq
    * @return
    */
  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] = {
    logger.info(f"RECEIVED: ${msg.id}")


    val flagContext: FlagContext = flags.findFlagContext(Some(config.getString("upstream.queue")))

    val unarchivedDocT: OptionT[Future, (List[String], DoclibDoc)] =
      for {
        doc: DoclibDoc <- OptionT(fetch(msg.id))
        if !docExtractor.isRunRecently(doc)
        started: UpdatedResult <- OptionT.liftF(flagContext.start(doc))
        unarchived <- OptionT.fromOption[Future](unarchive(doc))
        _ <- OptionT.liftF(persist(doc, unarchived))
        //  result ← OptionT(archive(doc, archivable)) //TODO add or remove this line of code
        _ <- OptionT(enqueue(unarchived, doc))
        _ <- OptionT.liftF(flagContext.end(doc, noCheck = started.changesMade))
      } yield unarchived -> doc

    val unarchivedDoc: Future[Option[(List[String], DoclibDoc)]] =
      unarchivedDocT.value

    unarchivedDoc.andThen {
      case Success(result) =>
        result match {
          case Some(r) =>
            incrementHandlerCount("success")
            supervisor.send(SupervisorMsg(id = r._2._id.toHexString))
            println(f"COMPLETE: ${msg.id} - Unarchived ${r._1.length}")
          case None =>
            incrementHandlerCount("empty_doc_error")
            val message = "Unidentified error occurred"
            logger.error(message, new Exception(message))
        }
      case Failure(e) =>
        logger.error("error during handle process", e)
        incrementHandlerCount("unknown_error")

        fetch(msg.id).onComplete {
          case Failure(e) => logger.error(s"error retrieving document", e)
          case Success(value) => value match {
            case Some(foundDoc) =>
              flagContext.error(foundDoc, noCheck = true).andThen {
                case Failure(e) => logger.error("error attempting error flag write", e)
              }
            case None =>
              val message = f"${msg.id} - no document found"
              logger.error(message, new Exception(message))
          }
        }
    }
  }

  private def incrementHandlerCount(labels: String*): Unit = {
    val labelsWithDefaults = Seq(consumerName, config.getString("upstream.queue")) ++ labels
    handlerCount.labels(labelsWithDefaults: _*).inc()
  }


}
