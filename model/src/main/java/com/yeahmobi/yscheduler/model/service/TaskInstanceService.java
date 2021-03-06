package com.yeahmobi.yscheduler.model.service;

import java.util.Date;
import java.util.List;

import com.yeahmobi.yscheduler.common.Paginator;
import com.yeahmobi.yscheduler.model.Task;
import com.yeahmobi.yscheduler.model.TaskInstance;
import com.yeahmobi.yscheduler.model.common.Query;
import com.yeahmobi.yscheduler.model.type.TaskInstanceStatus;

public interface TaskInstanceService {

    TaskInstance get(long id);

    TaskInstance get(long taskId, long workflowInstanceId);

    TaskInstance getLast(Task task, TaskInstance taskInstance);

    List<TaskInstance> list(Query query, long taskId, int pageNum, Paginator paginator);

    List<TaskInstance> listAllDependencyWait();

    List<TaskInstance> listAll(long taskId);

    List<TaskInstance> listByWorkflowInstanceId(long workflowInstanceId);

    void save(TaskInstance instance);

    List<TaskInstance> getAllUncompleteds();

    void updateStatus(Long instanceId, TaskInstanceStatus status);

    void deleteByWorkflowInstanceId(long workflowInstanceId);

    List<TaskInstance> listByWorkflowInstanceId(Long instanceId, int pageNum, Paginator paginator);

    boolean existUncompletedScheduled(long taskId);

    public boolean exist(long taskId, Date scheduleTime);

    public List<TaskInstance> listByWorkflowInstanceIdAndUserId(long workflowInstanceId, long userId, int pageNum,
                                                                Paginator paginator);

    public List<TaskInstance> listByWorkflowInstanceIdAndUserId(long workflowInstanceId, long userId);
}
