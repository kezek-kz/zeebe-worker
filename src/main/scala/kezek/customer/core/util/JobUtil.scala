package kezek.customer.core.util

import akka.http.scaladsl.model.{HttpMethod, HttpMethods, StatusCodes}
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.circe.parser.parse
import io.circe.generic.auto._
import io.zeebe.client.api.response.ActivatedJob
import io.zeebe.client.api.worker.JobClient
import kezek.customer.core.exception.ApiException
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._

object JobUtil {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def completeWithTechError(apiException: ApiException)(implicit client: JobClient, job: ActivatedJob): Unit = {
    log.debug(s"completeWithTechError() was called {exception: ${apiException.toErrorMessage}}")
    client
      .newFailCommand(job.getKey)
      .retries(job.getRetries - 1)
      .errorMessage(apiException.toErrorMessage.asJson.noSpaces)
      .send()
      .join()
  }

  def parseJsonFromHeader(key: String)(implicit job: ActivatedJob): Json = {
    val jsonString = job.getCustomHeaders.asScala.getOrElse(key, throw new NullPointerException(s"Missing $key header"))
    parseJson(jsonString)
  }

  def parseOptionJsonFromHeader(key: String)(implicit job: ActivatedJob): Option[Json] = {
    job.getCustomHeaders.asScala.get(key) map parseJson
  }

  def parseStringFromHeader(key: String)(implicit job: ActivatedJob): String = {
    job.getCustomHeaders.asScala.getOrElse(key, throw new NullPointerException(s"Missing $key header"))
  }

  def parseOptionStringFromHeader(key: String)(implicit job: ActivatedJob): Option[String] = {
    job.getCustomHeaders.asScala.get(key)
  }

  def parseJson(jsonString: String): Json = {
    parse(jsonString) match {
      case Right(json) => json
      case Left(decodingFailure) =>
        throw ApiException(StatusCodes.ServiceUnavailable, s"Failed to parse json: ${decodingFailure.message}")
    }
  }

  def getHttpMethod(method: String): HttpMethod = {
    method.toLowerCase match {
      case "get" => HttpMethods.GET
      case "post" => HttpMethods.POST
      case "put" => HttpMethods.PUT
      case "delete" => HttpMethods.DELETE
      case _ => throw new NoSuchMethodException("Invalid method. Supported http methods: [get, post, put, delete]")
    }
  }

  def exceptionHandler[Out](f: => Out)(implicit client: JobClient, job: ActivatedJob): Unit = {
    try {
      f
    } catch {
      case ex: Exception =>
        JobUtil.completeWithTechError(ApiException(StatusCodes.ServiceUnavailable, ex.getMessage))
    }
  }
}
