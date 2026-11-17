package com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.config;

import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.JdbcManagerLookup;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerChainResolver;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Chain.ManagerLookup;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.Entity.WorkflowRequest;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowRequestRepository;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.repository.WorkflowStepRepository;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service.WorkflowQueryService;
import com.Reusable.Workflow.Engine.Reusable.Workflow.Engine.service.WorkflowService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(WorkflowEmployeeProperties.class)
@EntityScan(basePackageClasses = WorkflowRequest.class)
@EnableJpaRepositories(basePackageClasses = WorkflowRequestRepository.class)
public class WorkflowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ManagerLookup managerLookup(JdbcTemplate jdbc, WorkflowEmployeeProperties props) {
        return new JdbcManagerLookup(jdbc, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public ManagerChainResolver managerChainResolver(ManagerLookup lookup) {
        return new ManagerChainResolver(lookup);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowService workflowService(WorkflowRequestRepository requestRepository,
                                           ManagerChainResolver resolver,
                                           ApplicationEventPublisher publisher) {
        return new WorkflowService(requestRepository, resolver, publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowQueryService workflowQueryService(WorkflowStepRepository stepRepository,
                                                     WorkflowRequestRepository requestRepository) {
        return new WorkflowQueryService(stepRepository, requestRepository);
    }
}