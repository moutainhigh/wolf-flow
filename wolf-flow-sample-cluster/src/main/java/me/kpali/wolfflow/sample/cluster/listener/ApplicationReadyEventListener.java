package me.kpali.wolfflow.sample.cluster.listener;

import me.kpali.wolfflow.core.scheduler.ITaskFlowScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 程序启动完成事件监听，在程序启动后启动任务流调度器
 * （必要）
 *
 * @author kpali
 */
@Component
public class ApplicationReadyEventListener implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    ITaskFlowScheduler taskFlowScheduler;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        taskFlowScheduler.startup();
    }
}