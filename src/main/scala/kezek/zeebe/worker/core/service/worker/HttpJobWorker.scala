package kezek.zeebe.worker.core.service.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.zeebe.client.api.response.ActivatedJob
import io.zeebe.client.api.worker.{JobClient, JobHandler}
import kezek.zeebe.worker.core.exception.ApiException.ErrorMessage
import kezek.zeebe.worker.core.exception.ApiException
import kezek.zeebe.worker.core.exception.ApiException.ErrorMessage
import kezek.zeebe.worker.core.util.JobUtil
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class HttpJobWorker(implicit val classicActorSystem: ActorSystem,
                    implicit val executionContext: ExecutionContext) extends JobHandler {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val config: Config = ConfigFactory.load()

  override def handle(_client: JobClient, _job: ActivatedJob): Unit = {
    implicit val client: JobClient = _client
    implicit val job: ActivatedJob = _job
    log.debug(s"handle() was called {elementId: ${job.getElementId}, bpmnProcessId: ${job.getBpmnProcessId}, workflowInstanceKey: ${job.getWorkflowInstanceKey}}")

    JobUtil.exceptionHandler {
      val hostKey = JobUtil.parseFromVariables[String]("host")

      val host = config.getString(s"hosts.$hostKey")
      val path = JobUtil.parseFromVariables[String]("path")
      val body = JobUtil.parseOptionFromVariables[Json]("body")
      val method = JobUtil.parseFromVariables[String]("method")
      val responseVariable = JobUtil.parseFromVariables[String]("responseVariable")

      val s = body.map(_.noSpaces).getOrElse("{}")
      send {
        HttpRequest(
          method = JobUtil.getHttpMethod(method),
          uri = Uri(s"$host$path"),
          entity = s
        )
      } map {
        case Right(responseBody) =>
          client
            .newCompleteCommand(job.getKey)
            .variables(Json.obj((responseVariable, responseBody.asJson)).noSpaces)
            .send()
            .join()
        case Left(exception) => {
          client
            .newThrowErrorCommand(job.getKey)
            .errorCode(exception.code.intValue().toString)
            .errorMessage(exception.toErrorMessage.asJson.noSpaces)
            .send()
            .join()
        }
      }
    }
  }

  def send(httpRequest: HttpRequest): Future[Either[ApiException, Option[Json]]] = {
    log.debug(s"send() was called {httpRequest: $httpRequest}")
    Http().singleRequest(httpRequest).flatMap {
      httpResponse =>
        httpResponse.status.intValue() match {
          case status if status >= 200 && status < 300 =>
            extractResponseBody(httpResponse).map(responseBody => Right(responseBody))
          case status if status >= 400 && status < 500 =>
            extractApiException(httpResponse).map(exception => Left(exception))
          case _ =>
            throw ApiException(StatusCodes.ServiceUnavailable, "Failed to process request")
        }
    }
  }

  def extractApiException(httpResponse: HttpResponse): Future[ApiException] = {
    extractResponseBody(httpResponse).map {
      case Some(json) =>
        json.hcursor.as[ErrorMessage] match {
          case Right(errorMessage) => ApiException(httpResponse.status, errorMessage.error, errorMessage.system)
          case Left(_) => ApiException(httpResponse.status, json.noSpaces)
        }
      case None => ApiException(httpResponse.status, "")
    }
  }

  def extractResponseBody(httpResponse: HttpResponse): Future[Option[Json]] = {
    Unmarshal(httpResponse.entity).to[String].map {
      case jsonString if jsonString.isEmpty =>
        log.debug(s"send() received empty entity {payload: $jsonString}")
        None
      case jsonString =>
        log.debug(s"send() received response entity {payload: $jsonString}")
        Some(JobUtil.parseJson(jsonString))
    }
  }

}
