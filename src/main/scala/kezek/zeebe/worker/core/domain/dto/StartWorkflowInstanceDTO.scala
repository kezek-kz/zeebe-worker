package kezek.zeebe.worker.core.domain.dto

case class StartWorkflowInstanceDTO(bpmnProcessId: String,
                                    workflowInstanceKey: Long,
                                    version: Int)
