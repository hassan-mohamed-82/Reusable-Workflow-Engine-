package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.WorkflowStatus;

public record WorkflowCompletedEvent(
        String requestType,
        Long referenceId,
        Long requesterEmpId,
        WorkflowStatus status,
        Long decidedByEmpId,
        String comment
) {
}