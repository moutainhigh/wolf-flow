package me.kpali.wolfflow.core.event;

import me.kpali.wolfflow.core.cluster.IClusterController;
import me.kpali.wolfflow.core.exception.TryLockException;
import me.kpali.wolfflow.core.logger.ITaskFlowLogger;
import me.kpali.wolfflow.core.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 任务流状态事件发布器
 *
 * @author kpali
 */
@Component
public class TaskFlowStatusEventPublisher {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private IClusterController clusterController;

    @Autowired
    private ITaskFlowLogger taskFlowLogger;

    /**
     * 发布任务流状态变更事件
     *
     * @param taskFlow
     * @param taskFlowContext
     * @param status
     * @param message
     * @param record
     */
    public void publishEvent(TaskFlow taskFlow, Map<String, Object> taskFlowContext, String status, String message, boolean record) {
        TaskFlowStatus taskFlowStatus = new TaskFlowStatus();
        taskFlowStatus.setTaskFlow(taskFlow);
        taskFlowStatus.setTaskFlowContext(taskFlowContext);
        taskFlowStatus.setStatus(status);
        taskFlowStatus.setMessage(message);
        if (record) {
            boolean locked = false;
            try {
                locked = this.clusterController.tryLock(
                        ClusterConstants.TASK_FLOW_LOG_LOCK,
                        ClusterConstants.LOG_LOCK_WAIT_TIME,
                        ClusterConstants.LOG_LOCK_LEASE_TIME,
                        TimeUnit.SECONDS);
                if (!locked) {
                    throw new TryLockException("获取任务流日志记录锁失败！");
                }
                TaskFlowContextWrapper taskFlowContextWrapper = new TaskFlowContextWrapper(taskFlowContext);
                Long taskFlowLogId = taskFlowContextWrapper.getValue(ContextKey.LOG_ID, Long.class);
                TaskFlowLog taskFlowLog = this.taskFlowLogger.get(taskFlowLogId);
                boolean isNewLog = false;
                if (taskFlowLog == null) {
                    isNewLog = true;
                    taskFlowLog = new TaskFlowLog();
                    taskFlowLog.setLogId(taskFlowLogId);
                    taskFlowLog.setTaskFlowId(taskFlow.getId());
                }
                taskFlowLog.setTaskFlow(taskFlow);
                taskFlowLog.setTaskFlowContext(taskFlowContext);
                taskFlowLog.setStatus(status);
                taskFlowLog.setMessage(message);
                if (isNewLog) {
                    this.taskFlowLogger.add(taskFlowLog);
                } else {
                    this.taskFlowLogger.update(taskFlowLog);
                }
            } finally {
                if (locked) {
                    this.clusterController.unlock(ClusterConstants.TASK_FLOW_LOG_LOCK);
                }
            }
        }
        TaskFlowStatusChangeEvent taskFlowStatusChangeEvent = new TaskFlowStatusChangeEvent(this, taskFlowStatus);
        this.eventPublisher.publishEvent(taskFlowStatusChangeEvent);
    }
}