package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service;


import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerChainResolver;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.StepStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.WorkflowStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowStep;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.StepActivatedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.WorkflowCompletedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
public class WorkflowService {

    private final WorkflowRequestRepository requestRepository;
    private final ManagerChainResolver chainResolver;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowService(WorkflowRequestRepository requestRepository,
                           ManagerChainResolver chainResolver,
                           ApplicationEventPublisher eventPublisher) {
        this.requestRepository = requestRepository;
        this.chainResolver = chainResolver;
        this.eventPublisher = eventPublisher;
    }

    // ───────────────────────── start ─────────────────────────

    @Transactional
    public WorkflowRequest start(String requestType, Long referenceId, Long requesterEmpId) {
        if (requestType == null || requestType.isBlank() || referenceId == null || requesterEmpId == null) {
            throw new WorkflowException("requestType, referenceId and requesterEmpId are required");
        }

        if (requestRepository.existsByRequestTypeAndReferenceId(requestType, referenceId)) {
            throw new WorkflowException(
                    "Workflow already started for " + requestType + " #" + referenceId);
        }

        List<Long> chain = chainResolver.resolve(requesterEmpId);

        WorkflowRequest request = new WorkflowRequest();
        request.setRequestType(requestType);
        request.setReferenceId(referenceId);
        request.setRequesterEmpId(requesterEmpId);

        if (chain.isEmpty()) {
            request.setStatus(WorkflowStatus.APPROVED);
            request.setCompletedAt(LocalDateTime.now());
        } else {
            for (int i = 0; i < chain.size(); i++) {
                WorkflowStep step = new WorkflowStep();
                step.setApproverEmpId(chain.get(i));
                step.setApprovalLevel(i + 1);
                step.setStatus(i == 0 ? StepStatus.PENDING : StepStatus.WAITING);
                request.addStep(step);
            }
            request.setCurrentLevel(1);
        }

        WorkflowRequest saved = requestRepository.save(request);

        if (chain.isEmpty()) {
            publishCompleted(saved, null, null);
        } else {
            publishStepActivated(saved, saved.getSteps().get(0));
        }
        return saved;
    }

    // ───────────────────────── approve ─────────────────────────

    @Transactional
    public WorkflowRequest approve(String requestType, Long referenceId,
                                   Long approverEmpId, String comment) {
        WorkflowRequest request = loadActiveRequest(requestType, referenceId);
        WorkflowStep current = findCurrentStep(request, approverEmpId);

        current.setStatus(StepStatus.APPROVED);
        current.setDecisionDate(LocalDateTime.now());
        current.setComment(comment);

        Optional<WorkflowStep> next = request.getSteps().stream()
                .filter(s -> s.getApprovalLevel().equals(current.getApprovalLevel() + 1))
                .findFirst();

        if (next.isPresent()) {
            next.get().setStatus(StepStatus.PENDING);
            request.setCurrentLevel(next.get().getApprovalLevel());
        } else {
            request.setStatus(WorkflowStatus.APPROVED);
            request.setCompletedAt(LocalDateTime.now());
            request.setCurrentLevel(null);
        }

        WorkflowRequest saved = requestRepository.save(request);

        if (next.isPresent()) {
            publishStepActivated(saved, next.get());
        } else {
            publishCompleted(saved, approverEmpId, comment);
        }
        return saved;
    }

    // ───────────────────────── reject ─────────────────────────

    @Transactional
    public WorkflowRequest reject(String requestType, Long referenceId,
                                  Long approverEmpId, String comment) {
        WorkflowRequest request = loadActiveRequest(requestType, referenceId);
        WorkflowStep current = findCurrentStep(request, approverEmpId);

        current.setStatus(StepStatus.REJECTED);
        current.setDecisionDate(LocalDateTime.now());
        current.setComment(comment);

        request.getSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.WAITING)
                .forEach(s -> s.setStatus(StepStatus.CANCELLED));

        request.setStatus(WorkflowStatus.REJECTED);
        request.setCompletedAt(LocalDateTime.now());
        request.setCurrentLevel(null);

        WorkflowRequest saved = requestRepository.save(request);
        publishCompleted(saved, approverEmpId, comment);
        return saved;
    }

    // ───────────────────────── helpers ─────────────────────────

    private WorkflowRequest loadActiveRequest(String requestType, Long referenceId) {
        WorkflowRequest request = requestRepository
                .findByRequestTypeAndReferenceId(requestType, referenceId)
                .orElseThrow(() -> new WorkflowException(
                        "Workflow not found for " + requestType + " #" + referenceId));

        if (request.getStatus() != WorkflowStatus.PENDING) {
            throw new WorkflowException("Workflow is already " + request.getStatus());
        }
        return request;
    }

    private WorkflowStep findCurrentStep(WorkflowRequest request, Long approverEmpId) {
        WorkflowStep current = request.getSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new WorkflowException("No pending step for this workflow"));

        if (!current.getApproverEmpId().equals(approverEmpId)) {
            throw new WorkflowException("It is not employee " + approverEmpId + "'s turn to decide");
        }
        return current;
    }

    private void publishStepActivated(WorkflowRequest request, WorkflowStep step) {
        eventPublisher.publishEvent(new StepActivatedEvent(
                request.getRequestType(),
                request.getReferenceId(),
                request.getRequesterEmpId(),
                step.getApproverEmpId(),
                step.getApprovalLevel()));
    }

    private void publishCompleted(WorkflowRequest request, Long decidedByEmpId, String comment) {
        eventPublisher.publishEvent(new WorkflowCompletedEvent(
                request.getRequestType(),
                request.getReferenceId(),
                request.getRequesterEmpId(),
                request.getStatus(),
                decidedByEmpId,
                comment));
    }
}