package kezek.customer.core.api.http.route

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.generic.auto._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import kezek.customer.core.codec.MainCodec
import kezek.customer.core.domain.dto.{DeployWorkflowsDTO, DeployedWorkflowsDTO, StartWorkflowInstanceDTO}
import kezek.customer.core.service.ZeebeService
import kezek.customer.core.util.HttpUtil

import javax.ws.rs._
import scala.util.{Failure, Success}

trait ZeebeHttpRoutes extends MainCodec {

  val zeebeService: ZeebeService

  def zeebeHttpRoutes: Route = {
    pathPrefix("zeebe") {
      concat(
        startProcess,
        publishMessage,
        deployWorkflows
      )
    }
  }

  @POST
  @Operation(
    summary = "Start workflow instance",
    description = "Starts workflow instance by specified processId",
    method = "POST",
    parameters = Array(
      new Parameter(name = "processId", in = ParameterIn.QUERY, required = true, example = "")
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[Json]),
          examples = Array(
            new ExampleObject(name = "Start some workflow", value = """{ "key": "value" }""")
          )
        )
      ),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "StartWorkflowInstanceResponse",
        content = Array(
          new Content(
            mediaType = "application/json",
            schema = new Schema(implementation = classOf[StartWorkflowInstanceDTO]),
            examples = Array(new ExampleObject(name = "StartWorkflowInstanceResponse", value = ""))
          )
        )
      )
    )
  )
  @Tag(name = "Zeebe")
  @Path("zeebe/start-process")
  def startProcess: Route = {
    post {
      path("start-process") {
        parameter("processId") { processId =>
          entity(as[Json]) { body =>
            onComplete(zeebeService.startProcess(processId, body)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Publish message",
    description = "Publishes message by specified correlationKey and messageName",
    method = "POST",
    parameters = Array(
      new Parameter(name = "correlationKey", in = ParameterIn.QUERY, required = true, example = ""),
      new Parameter(name = "messageName", in = ParameterIn.QUERY, required = true, example = "")
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[Json]),
          examples = Array(
            new ExampleObject(name = "Some message body", value = """{ "key": "value" }""")
          )
        )
      ),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "StartWorkflowInstanceResponse",
        content = Array(
          new Content(
            mediaType = "application/json",
            examples = Array(new ExampleObject(name = "MessagePublished", value = "{}"))
          )
        )
      )
    )
  )
  @Tag(name = "Zeebe")
  @Path("zeebe/publish-message")
  def publishMessage: Route = {
    post {
      path("publish-message") {
        parameters("messageName", "correlationKey") { (messageName, correlationKey) =>
          entity(as[Json]) { body =>
            onComplete(zeebeService.publishMessage(correlationKey, messageName, body)) {
              case Success(_) => complete()
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Start workflow instance",
    description = "Starts workflow instance by specified processId",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[DeployWorkflowsDTO]),
        )
      ),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "List of DeployedWorkflows",
        content = Array(
          new Content(
            mediaType = "application/json",
            schema = new Schema(implementation = classOf[DeployedWorkflowsDTO]),
            examples = Array(new ExampleObject(name = "List of DeployedWorkflows", value = ""))
          )
        )
      )
    )
  )
  @Tag(name = "Zeebe")
  @Path("zeebe/deploy-workflows")
  def deployWorkflows: Route = {
    post {
      path("deploy-workflows") {
        fileUploadAll("workflows") {
          byteSources => {
            onComplete(zeebeService.deployWorkflow(byteSources)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }
}
