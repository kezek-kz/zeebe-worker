package kezek.zeebe.worker.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import io.circe.Json
import io.zeebe.client.ZeebeClient
import kezek.zeebe.worker.core.domain.dto.{DeployedWorkflowsDTO, StartWorkflowInstanceDTO}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ZeebeService(implicit val zeebeClient: ZeebeClient,
                   implicit val executionContext: ExecutionContext,
                   implicit val actorSystem: ActorSystem[_]) {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def startProcess(processId: String, body: Json): Future[StartWorkflowInstanceDTO] = {
    log.debug(s"startProcess() was called {processId: $processId, body: ${body.noSpaces}}")
    val startedWorkflowInstance = zeebeClient
      .newCreateInstanceCommand()
      .bpmnProcessId(processId)
      .latestVersion()
      .variables(body.noSpaces)
      .send()
      .join()
    Future {
      StartWorkflowInstanceDTO(
        bpmnProcessId = startedWorkflowInstance.getBpmnProcessId,
        workflowInstanceKey = startedWorkflowInstance.getWorkflowInstanceKey,
        version = startedWorkflowInstance.getVersion
      )
    }
  }

  def publishMessage(correlationKey: String, messageName: String, body: Json): Future[Done] = {
    log.debug(s"publishMessage() was called {messageName: $messageName, correlationKey: $correlationKey, body: ${body.noSpaces}}")
    val startedWorkflowInstance = zeebeClient
      .newPublishMessageCommand()
      .messageName(messageName)
      .correlationKey(correlationKey)
      .variables(body.noSpaces)
      .send()
      .join()
    Future { Done }
  }

  def deployWorkflow(byteSource: Source[ByteString, _], fileInfo: FileInfo): Future[Seq[DeployedWorkflowsDTO]] = {
    log.debug(s"deployWorkflow() was called {fileName: ${fileInfo.fileName}}")
    val deploymentEvent =
      zeebeClient
        .newDeployCommand()
        .addResourceStream(byteSource.runWith(StreamConverters.asInputStream(5.minutes)), fileInfo.fileName)
        .send()
        .join()

    Future {
      deploymentEvent.getWorkflows.asScala.map { workflow =>
        DeployedWorkflowsDTO(
          bpmnProcessId = workflow.getBpmnProcessId,
          workflowKey = workflow.getWorkflowKey,
          version = workflow.getVersion
        )
      }.toSeq
    }
  }


}
