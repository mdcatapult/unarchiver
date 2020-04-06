package io.mdcatapult.doclib.handlers

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.concurrency.LimitedExecution
import io.mdcatapult.doclib.messages.{ArchiveMsg, DoclibMsg, PrefetchMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.doclib.models.{Derivative, DoclibDoc, DoclibDocExtractor, Origin}
import io.mdcatapult.doclib.util.DoclibFlags
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.unarchive.extractors.{Auto, Gzip, SevenZip}
import org.apache.commons.compress.archivers.ArchiveException
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
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
                       collection: MongoCollection[DoclibDoc]
                      ) extends LazyLogging {

  private val docExtractor = DoclibDocExtractor()

  lazy val flags = new DoclibFlags(config.getString("doclib.flag"))

  def enqueue(extracted: List[String], doc: DoclibDoc): Future[Option[Boolean]] = {
    // Let prefetch know that it is an unarchived derivative
    val derivativeMetadata = List[MetaValueUntyped](MetaString("derivative.type", "unarchived"))
    extracted.foreach(path => {
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
        metadata = Some(doc.metadata.getOrElse(Nil) ::: derivativeMetadata),
        derivative =  Some(true)
      ))
    })
    Future.successful(Some(true))
  }

  def persist(doc: DoclibDoc, unarchived: List[String]): Future[List[Derivative]] =
    writeLimit(collection, "save derivatives") {
      _.updateOne(equal("_id", doc._id),
        addEachToSet("derivatives", unarchived.map(path => Derivative("unarchived", path)):_*),
      ).toFutureOption().map(_ => doc.derivatives.getOrElse(List[Derivative]())
        .filter(d => d.`type` == "unarchived" && !unarchived.contains(d.path)))
    }

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

  def unarchive(document: DoclibDoc): Option[List[String]] =
    Try(document.mimetype match {
      // try as compressed archive, else try as compressed file
      case "application/gzip" => Try(new Auto(document.source).extract()) match {
        case Success(r) => r
        case Failure(_: ArchiveException) => new Gzip(document.source).extract()
        case Failure(e) => throw e
      }
      case "application/x-7z-compressed" => new SevenZip(document.source).extract()
      case _ => new Auto(document.source).extract()
    }) match {
      case Success(result) => Some(result)
      case Failure(exception) => throw exception
    }

  def fetch(id: String): Future[Option[DoclibDoc]] =
    readLimit(collection, "fetch document by id") {
      _.find(equal("_id", new ObjectId(id)))
        .first()
        .toFutureOption()
    }

  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] = {
    logger.info(f"RECEIVED: ${msg.id}")
    (for {
      doc: DoclibDoc <- OptionT(fetch(msg.id))
      if !docExtractor.isRunRecently(doc)
      started: UpdateResult <- OptionT(flags.start(doc))
      unarchived <- OptionT.fromOption[Future](unarchive(doc))
      _ <- OptionT(enqueue(unarchived, doc))
      _ <- OptionT(flags.end(doc, noCheck = started.getModifiedCount > 0))

    } yield (unarchived, doc)).value.andThen({
      case Success(result) => result match {
        case Some(r) =>
          supervisor.send(SupervisorMsg(id = r._2._id.toHexString))
          println(f"COMPLETE: ${msg.id} - Unarchived ${r._1.length}")
        case None => throw new Exception("Unidentified error occurred")
      }
      case Failure(_) =>
        Try(Await.result(fetch(msg.id), Duration.Inf)) match {
          case Success(value: Option[DoclibDoc]) => value match {
            case Some(doc) => flags.error(doc, noCheck = true)
            case _ => () // do nothing as error handling will capture
          }
          case Failure(_) => () // do nothing as error handling will capture
        }
    })
  }

}
