package kezek.zeebe.worker.core.service.worker

import com.typesafe.config.{Config, ConfigFactory}
import io.zeebe.client.api.response.ActivatedJob
import io.zeebe.client.api.worker.{JobClient, JobHandler}
import kezek.zeebe.worker.core.util.JobUtil
import org.slf4j.{Logger, LoggerFactory}
import scala.jdk.CollectionConverters._

class InitJobWorker extends JobHandler {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val config: Config = ConfigFactory.load()

  override def handle(_client: JobClient, _job: ActivatedJob): Unit = {
    implicit val client: JobClient = _client
    implicit val job: ActivatedJob = _job
    log.debug(s"handle() was called {elementId: ${job.getElementId}, bpmnProcessId: ${job.getBpmnProcessId}, workflowInstanceKey: ${job.getWorkflowInstanceKey}}")
    JobUtil.exceptionHandler {
      client
        .newCompleteCommand(job.getKey)
        .variables(
          Map(
            "workflowInstanceKey" -> job.getWorkflowInstanceKey,
            "environment" -> config.getString("env")
          ).asJava
        )
        .send()
        .join()
    }
  }

}
