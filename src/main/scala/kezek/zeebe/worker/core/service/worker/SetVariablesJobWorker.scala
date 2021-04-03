package kezek.zeebe.worker.core.service.worker

import io.circe.Json
import io.zeebe.client.api.response.ActivatedJob
import io.zeebe.client.api.worker.{JobClient, JobHandler}
import kezek.zeebe.worker.core.util.JobUtil
import org.slf4j.{Logger, LoggerFactory}

class SetVariablesJobWorker extends JobHandler {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def handle(_client: JobClient, _job: ActivatedJob): Unit = {
    implicit val client: JobClient = _client
    implicit val job: ActivatedJob = _job
    log.debug(s"handle() was called {elementId: ${job.getElementId}, bpmnProcessId: ${job.getBpmnProcessId}, workflowInstanceKey: ${job.getWorkflowInstanceKey}}")
    JobUtil.exceptionHandler {
      val variables = JobUtil.parseFromVariables[Json]("variables")
      client
        .newCompleteCommand(job.getKey)
        .variables(variables.noSpaces)
        .send()
        .join()
    }
  }

}
