package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerChainResolver;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.StepStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.WorkflowStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.StepActivatedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.WorkflowCompletedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowRequestRepository;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {

    private WorkflowRequestRepository repository;
    private ApplicationEventPublisher publisher;
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowRequestRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(WorkflowRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 5 -> 3 -> 2 -> 1 (top)
        var managers = Map.of(5L, 3L, 3L, 2L, 2L, 1L);
        var resolver = new ManagerChainResolver(id -> Optional.ofNullable(managers.get(id)));
        service = new WorkflowService(repository, resolver, publisher);
    }

    /** يبدأ طلب ويربطه بالـ repository الوهمي عشان approve/reject يلاقوه */
    private WorkflowRequest started() {
        WorkflowRequest r = service.start("EXCUSE", 15L, 5L);
        when(repository.findByRequestTypeAndReferenceId("EXCUSE", 15L))
                .thenReturn(Optional.of(r));
        clearInvocations(publisher);
        return r;
    }

    // ───────── start ─────────

    @Test
    void startCreatesStepsFirstPendingRestWaiting() {
        WorkflowRequest r = service.start("EXCUSE", 15L, 5L);

        assertEquals(WorkflowStatus.PENDING, r.getStatus());
        assertEquals(1, r.getCurrentLevel());
        assertEquals(3, r.getSteps().size());
        assertEquals(3L, r.getSteps().get(0).getApproverEmpId());
        assertEquals(StepStatus.PENDING, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.WAITING, r.getSteps().get(1).getStatus());
        assertEquals(StepStatus.WAITING, r.getSteps().get(2).getStatus());
        verify(publisher).publishEvent(new StepActivatedEvent("EXCUSE", 15L, 5L, 3L, 1));
    }

    @Test
    void topManagerRequestIsApprovedImmediately() {
        WorkflowRequest r = service.start("EXCUSE", 16L, 1L);

        assertEquals(WorkflowStatus.APPROVED, r.getStatus());
        assertTrue(r.getSteps().isEmpty());
        assertNotNull(r.getCompletedAt());
        verify(publisher).publishEvent(
                new WorkflowCompletedEvent("EXCUSE", 16L, 1L, WorkflowStatus.APPROVED, null, null));
    }

    @Test
    void startRejectsDuplicateAndMissingInput() {
        when(repository.existsByRequestTypeAndReferenceId("EXCUSE", 15L)).thenReturn(true);
        assertThrows(WorkflowException.class, () -> service.start("EXCUSE", 15L, 5L));
        assertThrows(WorkflowException.class, () -> service.start(null, 1L, 1L));
        assertThrows(WorkflowException.class, () -> service.start("EXCUSE", null, 1L));
        assertThrows(WorkflowException.class, () -> service.start("EXCUSE", 1L, null));
    }

    // ───────── approve ─────────

    @Test
    void approveActivatesNextStepAndNotifies() {
        WorkflowRequest r = started();

        service.approve("EXCUSE", 15L, 3L, "ok");

        assertEquals(StepStatus.APPROVED, r.getSteps().get(0).getStatus());
        assertEquals("ok", r.getSteps().get(0).getComment());
        assertNotNull(r.getSteps().get(0).getDecisionDate());
        assertEquals(StepStatus.PENDING, r.getSteps().get(1).getStatus());
        assertEquals(StepStatus.WAITING, r.getSteps().get(2).getStatus());
        assertEquals(2, r.getCurrentLevel());
        assertEquals(WorkflowStatus.PENDING, r.getStatus());
        verify(publisher).publishEvent(new StepActivatedEvent("EXCUSE", 15L, 5L, 2L, 2));
    }

    @Test
    void lastApprovalCompletesRequest() {
        WorkflowRequest r = started();
        service.approve("EXCUSE", 15L, 3L, null);
        service.approve("EXCUSE", 15L, 2L, null);
        clearInvocations(publisher);

        service.approve("EXCUSE", 15L, 1L, "fine");

        assertEquals(WorkflowStatus.APPROVED, r.getStatus());
        assertNotNull(r.getCompletedAt());
        assertNull(r.getCurrentLevel());
        r.getSteps().forEach(s -> assertEquals(StepStatus.APPROVED, s.getStatus()));
        verify(publisher).publishEvent(
                new WorkflowCompletedEvent("EXCUSE", 15L, 5L, WorkflowStatus.APPROVED, 1L, "fine"));
    }

    @Test
    void managerOutOfTurnCannotApprove() {
        WorkflowRequest r = started();

        assertThrows(WorkflowException.class, () -> service.approve("EXCUSE", 15L, 1L, null));

        assertEquals(StepStatus.PENDING, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.WAITING, r.getSteps().get(2).getStatus());
        verifyNoInteractions(publisher);
    }

    @Test
    void unknownRequestIsRejected() {
        assertThrows(WorkflowException.class, () -> service.approve("EXCUSE", 999L, 3L, null));
    }

    // ───────── reject ─────────

    @Test
    void rejectCancelsRemainingStepsAndCompletes() {
        WorkflowRequest r = started();

        service.reject("EXCUSE", 15L, 3L, "missing papers");

        assertEquals(StepStatus.REJECTED, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.CANCELLED, r.getSteps().get(1).getStatus());
        assertEquals(StepStatus.CANCELLED, r.getSteps().get(2).getStatus());
        assertEquals(WorkflowStatus.REJECTED, r.getStatus());
        assertNull(r.getCurrentLevel());
        verify(publisher).publishEvent(new WorkflowCompletedEvent(
                "EXCUSE", 15L, 5L, WorkflowStatus.REJECTED, 3L, "missing papers"));
    }

    @Test
    void rejectAtSecondLevelKeepsFirstApproval() {
        WorkflowRequest r = started();
        service.approve("EXCUSE", 15L, 3L, "ok");

        service.reject("EXCUSE", 15L, 2L, "no");

        assertEquals(StepStatus.APPROVED, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.REJECTED, r.getSteps().get(1).getStatus());
        assertEquals(StepStatus.CANCELLED, r.getSteps().get(2).getStatus());
    }

    @Test
    void cannotActOnFinishedRequest() {
        started();
        service.reject("EXCUSE", 15L, 3L, "no");

        assertThrows(WorkflowException.class, () -> service.reject("EXCUSE", 15L, 3L, "again"));
        assertThrows(WorkflowException.class, () -> service.approve("EXCUSE", 15L, 3L, "late"));
    }
}