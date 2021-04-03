package kezek.zeebe.worker.core.domain.dto

case class DeployedWorkflowsDTO(bpmnProcessId: String,
                                workflowKey: Long,
                                version: Int)
