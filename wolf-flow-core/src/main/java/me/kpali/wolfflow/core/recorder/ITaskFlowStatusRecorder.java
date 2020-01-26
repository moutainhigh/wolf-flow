package me.kpali.wolfflow.core.recorder;

import me.kpali.wolfflow.core.exception.TaskFlowStatusRecordException;
import me.kpali.wolfflow.core.model.TaskFlowStatus;

import java.util.List;

/**
 * 任务流状态记录器
 *
 * @author kpali
 */
public interface ITaskFlowStatusRecorder {
    /**
     * 获取任务流状态列表
     *
     * @return
     * @throws TaskFlowStatusRecordException
     */
    List<TaskFlowStatus> list() throws TaskFlowStatusRecordException;

    /**
     * 根据任务流ID获取任务流状态
     *
     * @param taskFlowId
     * @return
     * @throws TaskFlowStatusRecordException
     */
    TaskFlowStatus get(Long taskFlowId) throws TaskFlowStatusRecordException;

    /**
     * 新增或更新任务流状态
     *
     * @param taskFlowStatus
     * @throws TaskFlowStatusRecordException
     */
    void put(TaskFlowStatus taskFlowStatus) throws TaskFlowStatusRecordException;

    /**
     * 如果任务流不在处理中，新增或更新任务流状态
     *
     * @param taskFlowStatus
     * @return 成功后返回任务流状态，不成功则返回null
     * @throws TaskFlowStatusRecordException
     */
    TaskFlowStatus putIfNotInProgress(TaskFlowStatus taskFlowStatus) throws TaskFlowStatusRecordException;

    /**
     * 如果任务流正在处理中，则更新任务流状态为停止中
     *
     * @param taskFlowId
     * @return 成功后返回任务流状态，不成功则返回null
     * @throws TaskFlowStatusRecordException
     */
    TaskFlowStatus toStoppingIfInProgress(Long taskFlowId) throws TaskFlowStatusRecordException;

    /**
     * 删除任务流状态
     *
     * @param taskFlowId
     * @throws TaskFlowStatusRecordException
     */
    void remove(Long taskFlowId) throws TaskFlowStatusRecordException;
}