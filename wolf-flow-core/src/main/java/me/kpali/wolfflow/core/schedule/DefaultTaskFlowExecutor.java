package me.kpali.wolfflow.core.schedule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import me.kpali.wolfflow.core.event.TaskStatusChangeEvent;
import me.kpali.wolfflow.core.exception.TaskFlowExecuteFailException;
import me.kpali.wolfflow.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 任务流执行器的默认实现
 *
 * @author kpali
 */
@Component
public class DefaultTaskFlowExecutor implements ITaskFlowExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultTaskFlowExecutor.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ITaskStatusRecorder taskStatusRecorder;

    @Override
    public void beforeExecute(TaskFlow taskFlow, TaskFlowContext taskFlowContext) throws Exception {
        taskFlowContext.put("taskFlowId", taskFlow.getId().toString());
    }

    @Override
    public void execute(TaskFlow taskFlow, TaskFlowContext taskFlowContext,
                        Integer taskFlowExecutorCorePoolSize, Integer taskFlowExecutorMaximumPoolSize) throws Exception {
        // 初始化线程池
        ThreadFactory executorThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("taskFlowExecutor-pool-%d").build();
        ExecutorService executorThreadPool = new ThreadPoolExecutor(taskFlowExecutorCorePoolSize, taskFlowExecutorMaximumPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024), executorThreadFactory, new ThreadPoolExecutor.AbortPolicy());

        Map<Long, Task> idToTaskMap = new HashMap<>();
        taskFlow.getTaskList().forEach(task -> {
            idToTaskMap.put(task.getId(), task);
        });
        // 计算节点入度
        Map<Long, Integer> taskIdToInDegreeMap = new HashMap<>();
        taskFlow.getTaskList().forEach(task -> {
            int inDegree = 0;
            for (Link link : taskFlow.getLinkList()) {
                if (link.getTarget().equals(task.getId())) {
                    inDegree++;
                }
            }
            taskIdToInDegreeMap.put(task.getId(), inDegree);
        });
        // 从入度为0的节点开始执行
        ConcurrentHashMap<Long, String> taskIdToStatusMap = new ConcurrentHashMap<>();
        for (Long taskId : taskIdToInDegreeMap.keySet()) {
            int inDegree = taskIdToInDegreeMap.get(taskId);
            if (inDegree == 0) {
                taskIdToStatusMap.put(taskId, TaskStatusEnum.WAIT_FOR_EXECUTE.getCode());
                Task task = idToTaskMap.get(taskId);
                this.publishTaskStatusChangeEvent(task, taskFlow.getId(), null, TaskStatusEnum.WAIT_FOR_EXECUTE.getCode(), null);
            }
        }
        boolean isSuccess = true;
        while (!taskIdToStatusMap.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Long taskId : taskIdToStatusMap.keySet()) {
                String taskStatus = taskIdToStatusMap.get(taskId);
                if (TaskStatusEnum.WAIT_FOR_EXECUTE.getCode().equals(taskStatus)) {
                    // 等待执行，将节点状态改为执行中，并将任务加入线程池
                    Task task = idToTaskMap.get(taskId);
                    taskIdToStatusMap.put(task.getId(), TaskStatusEnum.EXECUTING.getCode());
                    this.publishTaskStatusChangeEvent(task, taskFlow.getId(), null, TaskStatusEnum.EXECUTING.getCode(), null);
                    executorThreadPool.execute(() -> {
                        TaskContext taskContext = new TaskContext();
                        try {
                            task.execute(taskFlowContext, taskContext);
                            taskIdToStatusMap.put(task.getId(), TaskStatusEnum.EXECUTE_SUCCESS.getCode());
                            this.publishTaskStatusChangeEvent(task, taskFlow.getId(), taskContext, TaskStatusEnum.EXECUTE_SUCCESS.getCode(), null);
                        } catch (Exception e) {
                            log.error("任务执行失败！任务ID：" + task.getId() + " 异常信息：" + e.getMessage(), e);
                            taskIdToStatusMap.put(task.getId(), TaskStatusEnum.EXECUTE_FAILURE.getCode());
                            this.publishTaskStatusChangeEvent(task, taskFlow.getId(), taskContext, TaskStatusEnum.EXECUTE_FAILURE.getCode(), e.getMessage());
                        }
                    });
                } else if (TaskStatusEnum.EXECUTE_SUCCESS.getCode().equals(taskStatus)) {
                    // 执行成功，将子节点的入度减1，如果子节点入度为0，则将子节点状态设置为等待执行并加入状态检查，最后将此节点移除状态检查
                    for (Link link : taskFlow.getLinkList()) {
                        if (link.getSource().equals(taskId)) {
                            Long childTaskId = link.getTarget();
                            int childTaskInDegree = taskIdToInDegreeMap.get(childTaskId);
                            childTaskInDegree--;
                            taskIdToInDegreeMap.put(childTaskId, childTaskInDegree);
                            if (childTaskInDegree == 0) {
                                taskIdToStatusMap.put(childTaskId, TaskStatusEnum.WAIT_FOR_EXECUTE.getCode());
                                Task childTask = idToTaskMap.get(childTaskId);
                                this.publishTaskStatusChangeEvent(childTask, taskFlow.getId(), null, TaskStatusEnum.WAIT_FOR_EXECUTE.getCode(), null);
                            }
                        }
                    }
                    taskIdToStatusMap.remove(taskId);
                } else if (TaskStatusEnum.EXECUTE_FAILURE.getCode().equals(taskStatus)
                        || TaskStatusEnum.SKIPPED.getCode().equals(taskStatus)) {
                    if (TaskStatusEnum.EXECUTE_FAILURE.getCode().equals(taskStatus)) {
                        isSuccess = false;
                    }
                    // 执行失败 或者 跳过，将子节点状态设置为跳过，并将此节点移除状态检查
                    for (Link link : taskFlow.getLinkList()) {
                        if (link.getSource().equals(taskId)) {
                            Long childTaskId = link.getTarget();
                            taskIdToStatusMap.put(childTaskId, TaskStatusEnum.SKIPPED.getCode());
                            Task childTask = idToTaskMap.get(childTaskId);
                            this.publishTaskStatusChangeEvent(childTask, taskFlow.getId(), null, TaskStatusEnum.SKIPPED.getCode(), null);
                        }
                    }
                    taskIdToStatusMap.remove(taskId);
                }
            }
        }
        if (!isSuccess) {
            throw new TaskFlowExecuteFailException("至少有一个任务执行失败！");
        }
    }

    @Override
    public void afterExecute(TaskFlow taskFlow, TaskFlowContext taskFlowContext) throws Exception {
        // 不做任何操作
    }

    /**
     * 发布任务状态变更事件
     *
     * @param task
     * @param taskFlowId
     * @param taskContext
     * @param status
     * @param message
     */
    private void publishTaskStatusChangeEvent(Task task, Long taskFlowId, TaskContext taskContext, String status, String message) {
        TaskStatus taskStatus = new TaskStatus();
        taskStatus.setTask(task);
        taskStatus.setTaskFlowId(taskFlowId);
        taskStatus.setContext(taskContext);
        taskStatus.setStatus(status);
        taskStatus.setMessage(message);
        TaskStatusChangeEvent taskStatusChangeEvent = new TaskStatusChangeEvent(this, taskStatus);
        this.eventPublisher.publishEvent(taskStatusChangeEvent);
    }
}
