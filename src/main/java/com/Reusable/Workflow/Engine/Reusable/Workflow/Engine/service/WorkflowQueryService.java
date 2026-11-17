package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.StepStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowRequestRepository;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowStepRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public class WorkflowQueryService {

    private final WorkflowStepRepository stepRepository;
    private final WorkflowRequestRepository requestRepository;

    public WorkflowQueryService(WorkflowStepRepository stepRepository,
                                WorkflowRequestRepository requestRepository) {
        this.stepRepository = stepRepository;
        this.requestRepository = requestRepository;
    }

    /** طلبات منتظرة موافقة المدير ده (خطوته PENDING فقط). */
    @Transactional(readOnly = true)
    public List<PendingApproval> findPendingFor(Long approverEmpId) {
        return stepRepository.findByApproverAndStatus(approverEmpId, StepStatus.PENDING).stream()
                .map(s -> new PendingApproval(
                        s.getWorkflowRequest().getRequestType(),
                        s.getWorkflowRequest().getReferenceId(),
                        s.getWorkflowRequest().getRequesterEmpId(),
                        s.getApprovalLevel(),
                        s.getWorkflowRequest().getCreatedAt()))
                .toList();
    }

    /** تاريخ طلب معيّن: الطلب + كل خطواته مرتبة. */
    @Transactional(readOnly = true)
    public Optional<WorkflowRequest> findHistory(String requestType, Long referenceId) {
        return requestRepository.findWithSteps(requestType, referenceId);
    }

    public record PendingApproval(
            String requestType,
            Long referenceId,
            Long requesterEmpId,
            Integer approvalLevel,
            java.time.LocalDateTime createdAt
    ) {
    }
}