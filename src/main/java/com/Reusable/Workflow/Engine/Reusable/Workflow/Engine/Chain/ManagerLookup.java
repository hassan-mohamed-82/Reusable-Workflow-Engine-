package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain;

import java.util.Optional;

public interface ManagerLookup {

    Optional<Long> findManagerId(Long employeeId);
}