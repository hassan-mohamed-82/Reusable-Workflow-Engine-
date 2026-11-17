package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {

    Optional<WorkflowRequest> findByRequestTypeAndReferenceId(String requestType, Long referenceId);

    boolean existsByRequestTypeAndReferenceId(String requestType, Long referenceId);

    @Query("""
       select distinct r from WorkflowRequest r
       left join fetch r.steps
       where r.requestType = :requestType and r.referenceId = :referenceId
       """)
    Optional<WorkflowRequest> findWithSteps(@Param("requestType") String requestType,
                                            @Param("referenceId") Long referenceId);
}