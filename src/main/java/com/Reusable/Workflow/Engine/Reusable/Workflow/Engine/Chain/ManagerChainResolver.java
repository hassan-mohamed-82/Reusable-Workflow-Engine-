package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain;



import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ManagerChainResolver {

    private static final int MAX_LEVELS = 20;

    private final ManagerLookup managerLookup;

    public ManagerChainResolver(ManagerLookup managerLookup) {
        this.managerLookup = managerLookup;
    }

    public List<Long> resolve(Long employeeId) {
        List<Long> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(employeeId);

        Long current = employeeId;
        while (true) {
            Optional<Long> manager = managerLookup.findManagerId(current);
            if (manager.isEmpty()) {
                break;
            }
            Long managerId = manager.get();

            if (!visited.add(managerId)) {
                throw new WorkflowException(
                        "Circular manager chain detected at employee " + managerId);
            }
            if (chain.size() >= MAX_LEVELS) {
                throw new WorkflowException("Manager chain exceeds " + MAX_LEVELS + " levels");
            }

            chain.add(managerId);
            current = managerId;
        }
        return chain;
    }
}