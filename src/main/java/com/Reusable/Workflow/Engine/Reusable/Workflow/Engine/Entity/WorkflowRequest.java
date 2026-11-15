package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity;


import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_request")
@Getter
@Setter
public class WorkflowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "requester_emp_id", nullable = false)
    private Long requesterEmpId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowStatus status = WorkflowStatus.PENDING;

    @Column(name = "current_level")
    private Integer currentLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "workflowRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("approvalLevel ASC")
    private List<WorkflowStep> steps = new ArrayList<>();

    public void addStep(WorkflowStep step) {
        step.setWorkflowRequest(this);
        steps.add(step);
    }
}
