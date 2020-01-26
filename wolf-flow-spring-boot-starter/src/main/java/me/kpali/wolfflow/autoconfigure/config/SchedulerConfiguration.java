package me.kpali.wolfflow.autoconfigure.config;

import me.kpali.wolfflow.autoconfigure.properties.SchedulerProperties;
import me.kpali.wolfflow.core.config.SchedulerConfig;
import me.kpali.wolfflow.core.scheduler.TaskFlowScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"me.kpali.wolfflow.core.scheduler"})
public class SchedulerConfiguration {
    @Bean
    public SchedulerConfig getSchedulerConfig(SchedulerProperties schedulerProperties) {
        Integer execRequestScanInterval = schedulerProperties.getExecRequestScanInterval();
        Integer cronTaskFlowScanInterval = schedulerProperties.getCronTaskFlowScanInterval();
        Integer corePoolSize = schedulerProperties.getCorePoolSize();
        Integer maximumPoolSize = schedulerProperties.getMaximumPoolSize();
        Boolean allowParallel = schedulerProperties.getAllowParallel();
        SchedulerConfig schedulerConfig = new SchedulerConfig();
        schedulerConfig.setExecRequestScanInterval(execRequestScanInterval);
        schedulerConfig.setCronTaskFlowScanInterval(cronTaskFlowScanInterval);
        schedulerConfig.setCorePoolSize(corePoolSize);
        schedulerConfig.setMaximumPoolSize(maximumPoolSize);
        schedulerConfig.setAllowParallel(allowParallel);
        return schedulerConfig;
    }

    @Bean(initMethod = "startup")
    public TaskFlowScheduler getTaskFlowScheduler(SchedulerConfig schedulerConfig) {
        return new TaskFlowScheduler(schedulerConfig);
    }
}