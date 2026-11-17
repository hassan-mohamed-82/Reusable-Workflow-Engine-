package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event;
public record StepActivatedEvent(
        String requestType,
        Long referenceId,
        Long requesterEmpId,
        Long approverEmpId,
        Integer approvalLevel
) {
}