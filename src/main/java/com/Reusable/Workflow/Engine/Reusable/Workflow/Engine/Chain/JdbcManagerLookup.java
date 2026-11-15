package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain;


import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Common.Exceptions.WorkflowException;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.config.WorkflowEmployeeProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class JdbcManagerLookup implements ManagerLookup {

    private static final Pattern IDENTIFIER =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private final JdbcTemplate jdbc;
    private final String sql;

    public JdbcManagerLookup(JdbcTemplate jdbc, WorkflowEmployeeProperties props) {
        this.jdbc = jdbc;
        this.sql = "SELECT " + safe(props.getManagerColumn())
                + " FROM " + safe(props.getTable())
                + " WHERE " + safe(props.getIdColumn()) + " = ?";
    }

    @Override
    public Optional<Long> findManagerId(Long employeeId) {
        List<Long> rows = jdbc.query(sql, (rs, i) -> rs.getObject(1, Long.class), employeeId);
        if (rows.isEmpty()) {
            throw new WorkflowException("Employee not found: " + employeeId);
        }
        return rows.stream().filter(Objects::nonNull).findFirst();
    }

    private static String safe(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new WorkflowException("Invalid SQL identifier in workflow config: " + identifier);
        }
        return identifier;
    }
}