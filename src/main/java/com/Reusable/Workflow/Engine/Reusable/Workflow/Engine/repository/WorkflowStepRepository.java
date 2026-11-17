package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Enums.StepStatus;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    @Query("""
           select s from WorkflowStep s
           join fetch s.workflowRequest r
           where s.approverEmpId = :approverEmpId
             and s.status = :status
           order by r.createdAt asc
           """)
    List<WorkflowStep> findByApproverAndStatus(@Param("approverEmpId") Long approverEmpId,
                                               @Param("status") StepStatus status);
}