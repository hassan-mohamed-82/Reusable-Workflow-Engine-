package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow.employee")
@Getter
@Setter
public class WorkflowEmployeeProperties {

    private String table = "employee";
    private String idColumn = "id";
    private String managerColumn = "manager_id";
}
