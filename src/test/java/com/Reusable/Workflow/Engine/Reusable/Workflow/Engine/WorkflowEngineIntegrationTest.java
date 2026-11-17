package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.StepStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.WorkflowStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.StepActivatedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.event.WorkflowCompletedEvent;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service.WorkflowQueryService;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = WorkflowEngineIntegrationTest.TestApp.class,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/workflow/workflow_engine.sql,classpath:test-employee.sql",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@RecordApplicationEvents
class WorkflowEngineIntegrationTest {

    /** تطبيق وهمي للتست فقط: الـ Auto-Configuration بتاعة المحرك هي اللي بتشتغل */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired WorkflowService service;
    @Autowired WorkflowQueryService queryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    @BeforeEach
    void clean() {
        jdbc.update("delete from workflow_request");   // الخطوات بتتمسح بالـ CASCADE
    }

    @Test
    void fullApprovalFlowOnRealDatabase() {
        service.start("EXCUSE", 15L, 5L);
        service.approve("EXCUSE", 15L, 3L, "ok 3");
        service.approve("EXCUSE", 15L, 2L, "ok 2");
        service.approve("EXCUSE", 15L, 1L, "ok 1");

        WorkflowRequest r = queryService.findHistory("EXCUSE", 15L).orElseThrow();
        assertEquals(WorkflowStatus.APPROVED, r.getStatus());
        assertNotNull(r.getCompletedAt());
        assertNull(r.getCurrentLevel());
        assertEquals(3, r.getSteps().size());
        r.getSteps().forEach(s -> {
            assertEquals(StepStatus.APPROVED, s.getStatus());
            assertNotNull(s.getDecisionDate());
        });
        assertEquals("ok 2", r.getSteps().get(1).getComment());

        // start -> مدير 3، approve(3) -> مدير 2، approve(2) -> مدير 1 = 3 تفعيلات
        assertEquals(3, events.stream(StepActivatedEvent.class).count());
        // approve(1) هي الأخيرة، فتنشر Completed واحد
        assertEquals(1, events.stream(WorkflowCompletedEvent.class).count());
    }

    @Test
    void rejectCancelsRemainingStepsAndPublishesCompleted() {
        service.start("EXCUSE", 20L, 5L);
        service.approve("EXCUSE", 20L, 3L, "ok");
        service.reject("EXCUSE", 20L, 2L, "no");

        WorkflowRequest r = queryService.findHistory("EXCUSE", 20L).orElseThrow();
        assertEquals(WorkflowStatus.REJECTED, r.getStatus());
        assertEquals(StepStatus.APPROVED, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.REJECTED, r.getSteps().get(1).getStatus());
        assertEquals(StepStatus.CANCELLED, r.getSteps().get(2).getStatus());

        List<WorkflowCompletedEvent> completed = events.stream(WorkflowCompletedEvent.class).toList();
        assertEquals(1, completed.size());
        assertEquals(WorkflowStatus.REJECTED, completed.get(0).status());
        assertEquals("no", completed.get(0).comment());
    }

    @Test
    void pendingScreenShowsOnlyManagerWhoseTurnItIs() {
        service.start("EXCUSE", 30L, 5L);

        assertEquals(1, queryService.findPendingFor(3L).size());
        assertTrue(queryService.findPendingFor(2L).isEmpty());   // WAITING مش بيظهر
        assertTrue(queryService.findPendingFor(1L).isEmpty());

        service.approve("EXCUSE", 30L, 3L, null);

        assertTrue(queryService.findPendingFor(3L).isEmpty());
        assertEquals(1, queryService.findPendingFor(2L).size());
        assertEquals(30L, queryService.findPendingFor(2L).get(0).referenceId());
    }

    @Test
    void topManagerRequestIsApprovedImmediately() {
        service.start("EXCUSE", 40L, 1L);

        WorkflowRequest r = queryService.findHistory("EXCUSE", 40L).orElseThrow();
        assertEquals(WorkflowStatus.APPROVED, r.getStatus());
        assertTrue(r.getSteps().isEmpty());
        assertEquals(1, events.stream(WorkflowCompletedEvent.class).count());
    }

    @Test
    void duplicateStartIsRejected() {
        service.start("EXCUSE", 50L, 5L);
        assertThrows(WorkflowException.class, () -> service.start("EXCUSE", 50L, 5L));
    }

    @Test
    void sameReferenceIdDifferentTypeIsAllowed() {
        service.start("EXCUSE", 60L, 5L);
        service.start("MISSION", 60L, 5L);

        assertTrue(queryService.findHistory("EXCUSE", 60L).isPresent());
        assertTrue(queryService.findHistory("MISSION", 60L).isPresent());
    }

    @Test
    void managerOutOfTurnCannotApproveAndDatabaseIsUnchanged() {
        service.start("EXCUSE", 70L, 5L);

        assertThrows(WorkflowException.class, () -> service.approve("EXCUSE", 70L, 1L, null));

        WorkflowRequest r = queryService.findHistory("EXCUSE", 70L).orElseThrow();
        assertEquals(StepStatus.PENDING, r.getSteps().get(0).getStatus());
        assertEquals(StepStatus.WAITING, r.getSteps().get(2).getStatus());
    }
}