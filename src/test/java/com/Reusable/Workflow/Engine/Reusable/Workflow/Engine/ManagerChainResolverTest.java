package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerChainResolver;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerLookup;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.Optional;


class ManagerChainResolverTest {

    private ManagerChainResolver resolverFor(Map<Long, Long> managers) {
        ManagerLookup lookup = id -> Optional.ofNullable(managers.get(id));
        return new ManagerChainResolver(lookup);
    }


    @Test
    void walksUpToTopManager() {
        var resolver = resolverFor(Map.of(5L, 3L, 3L, 2L, 2L, 1L));
        assertEquals(List.of(3L, 2L, 1L), resolver.resolve(5L));
    }

    @Test
    void topManagerHasEmptyChain() {
        var resolver = resolverFor(Map.of(5L, 3L));
        assertTrue(resolver.resolve(3L).isEmpty());
    }

    @Test
    void detectsCycle() {
        var resolver = resolverFor(Map.of(1L, 2L, 2L, 1L));
        assertThrows(WorkflowException.class, () -> resolver.resolve(1L));
    }

    @Test
    void detectsSelfManager() {
        var resolver = resolverFor(Map.of(1L, 1L));
        assertThrows(WorkflowException.class, () -> resolver.resolve(1L));
    }
}