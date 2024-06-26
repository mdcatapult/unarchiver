package io.mdcatapult.doclib.handlers

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import io.mdcatapult.doclib.consumer.AbstractHandler
import io.mdcatapult.doclib.flag.MongoFlagContext
import io.mdcatapult.doclib.messages.{DoclibMsg, PrefetchMsg, SupervisorMsg}
import io.mdcatapult.doclib.models._
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.unarchive.extractors.{Auto, Gzip, SevenZip}
import io.mdcatapult.util.concurrency.LimitedExecution
import io.mdcatapult.util.models.Version
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.pekko.stream.connectors.amqp.scaladsl.CommittableReadResult
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.result.InsertManyResult
import play.api.libs.json.Json

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class UnarchiveHandler(prefetch: Sendable[PrefetchMsg],
                       supervisor: Sendable[SupervisorMsg],
                       val readLimiter: LimitedExecution,
                       val writeLimiter: LimitedExecution)
                      (implicit ec: ExecutionContext,
                       config: Config,
                       collection: MongoCollection[DoclibDoc],
                       derivativesCollection: MongoCollection[ParentChildMapping],
                       appConfig: AppConfig) extends AbstractHandler[DoclibMsg, UnarchiveHandlerResult] {

  private val version: Version = Version.fromConfig(config)

  /** Handler for the unarchive consumer.
    *
    * @param msg message to process
    * @return
    */
  override def handle(doclibMsgWrapper: CommittableReadResult): Future[(CommittableReadResult, Try[UnarchiveHandlerResult])] = {

    Try {
      Json.parse(doclibMsgWrapper.message.bytes.utf8String).as[DoclibMsg]
    } match {
      case Success(msg: DoclibMsg) => {
        logReceived(msg.id)
        val flagContext = new MongoFlagContext(appConfig.name, version, collection, nowUtc)

        val unarchiveProcess =
          for {
            doc: DoclibDoc <- OptionT(findDocById(collection, msg.id))
            if !flagContext.isRunRecently(doc)
            started: UpdatedResult <- OptionT.liftF(flagContext.start(doc))
            unarchived <- OptionT.fromOption[Future](unarchive(doc))
            _ <- OptionT.liftF(persist(doc, unarchived))
            _ <- OptionT(enqueue(unarchived, doc))
            _ <- OptionT.liftF(flagContext.end(doc, noCheck = started.changesMade))
            finishedDoc: DoclibDoc <- OptionT(findDocById(collection, msg.id))
          } yield UnarchiveHandlerResult(finishedDoc, Some(unarchived))

        val finalResult = unarchiveProcess.value.transformWith({
          case Success(Some(value: UnarchiveHandlerResult)) => Future((doclibMsgWrapper, Success(value)))
          case Success(None) => Future((doclibMsgWrapper, Failure(new Exception(s"No unarchive result was present for ${msg.id}"))))
          case Failure(e) => Future((doclibMsgWrapper, Failure(e)))
        })

        postHandleProcess(
          documentId = msg.id,
          handlerResult = finalResult,
          flagContext = flagContext,
          supervisorQueue = supervisor,
          collection = collection
        )
      }
      case Failure(e) => Future.successful((doclibMsgWrapper, Failure(e)))
    }
  }

  def enqueue(extracted: List[String], doc: DoclibDoc): Future[Option[Boolean]] = {
    // Let prefetch know that it is an unarchived derivative
    val derivativeMetadata = List[MetaValueUntyped](MetaString("derivative.type", config.getString("consumer.name")))
    extracted.foreach(path => {
      prefetch.send(PrefetchMsg(
        source = path,
        origins = Some(List(Origin(
          scheme = "mongodb",
          metadata = Some(List(
            MetaString("db", config.getString("mongo.doclib-database")),
            MetaString("collection", config.getString("mongo.documents-collection")),
            MetaString("_id", doc._id.toHexString)))
        ))),
        tags = doc.tags,
        metadata = Some(doc.metadata.getOrElse(Nil) ::: derivativeMetadata),
        derivative = Some(true)
      ))
    })
    Future.successful(Some(true))
  }

  def persist(doc: DoclibDoc, unarchived: List[String]): Future[Option[InsertManyResult]] =
    derivativesCollection.insertMany(createDerivativesFromPaths(doc, unarchived)).toFutureOption()

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

  /**
    * Create list of parent child mappings
    *
    * @param doc   DoclibDoc
    * @param paths List[String]
    * @return List[Derivative] unique list of derivatives
    */
  def createDerivativesFromPaths(doc: DoclibDoc, paths: List[String]): List[ParentChildMapping] = {
    //TODO This same pattern is used in other consumers so maybe we can move to a shared lib in common or a shared consumer lib.
    paths.map(path =>
      ParentChildMapping(
        _id = UUID.randomUUID(),
        childPath = path,
        parent = doc._id,
        consumer = Some(appConfig.name)
      )
    )
  }

}
