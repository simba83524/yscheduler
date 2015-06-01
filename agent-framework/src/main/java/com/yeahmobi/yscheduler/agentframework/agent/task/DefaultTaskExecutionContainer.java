package com.yeahmobi.yscheduler.agentframework.agent.task;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yeahmobi.yscheduler.agentframework.agent.daemon.LogTransfer;
import com.yeahmobi.yscheduler.agentframework.agent.event.EventHandler;
import com.yeahmobi.yscheduler.agentframework.agent.event.EventMapper;
import com.yeahmobi.yscheduler.agentframework.agent.event.TaskSubmitionEventHandler;
import com.yeahmobi.yscheduler.agentframework.agent.task.TaskTransaction.Meta;
import com.yeahmobi.yscheduler.agentframework.exception.TaskNotFoundException;
import com.yeahmobi.yscheduler.agentframework.exception.TaskSubmitException;
import com.yeahmobi.yscheduler.agentframework.exception.TaskTransactionCreationException;

/**
 * @author Leo.Liang
 */
public class DefaultTaskExecutionContainer implements TaskExecutionContainer {

    private static final Logger                        log                 = LoggerFactory.getLogger(DefaultTaskExecutionContainer.class);

    private ExecutorService                            workerPool          = Executors.newCachedThreadPool();
    private final ConcurrentMap<Long, TaskTransaction> runningTransactions = new ConcurrentHashMap<Long, TaskTransaction>();

    private TaskTransactionManager                     taskTransactionManager;
    private EventMapper                                eventMapper;
    private LogTransfer                                logTransfer;

    public void setTaskTransactionManager(TaskTransactionManager transactionManager) {
        this.taskTransactionManager = transactionManager;
    }

    public void setEventMapper(EventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public void setLogTransfer(LogTransfer logTransfer) {
        this.logTransfer = logTransfer;
    }

    @SuppressWarnings("unchecked")
    public void init() throws TaskNotFoundException, IOException, TaskSubmitException, TaskTransactionCreationException {
        // 扫描未结束的tx，按照type，构建 AgentTask，submit
        List<TaskTransaction> allTransaction = this.taskTransactionManager.getAllTransaction();
        if ((allTransaction != null) && (allTransaction.size() > 0)) {
            for (TaskTransaction tx : allTransaction) {
                AgentTask task = loadAgentTask(tx.getMeta());
                TaskTransaction<AgentTask> transaction = this.taskTransactionManager.getTransaction(tx.getId(), task);

                // TODO add: log搬运完成的tx会被删除，如存在，则说明未搬运完成，故此处重新搬运该transaction的log
                // this.logTransfer.submit(transaction);

                if (!tx.getMeta().getStatus().isCompleted()) {
                    try {
                        log.info("Task(transactionId=" + tx.getId() + ") is continued because status is "
                                 + tx.getMeta().getStatus());

                        // TODO delete:log搬运完成的tx会被删除，如存在，则说明未搬运完成，故此处重新搬运该transaction的log
                        this.logTransfer.submit(transaction);

                        execTransaction(transaction);

                    } catch (Exception e) {
                        log.error(String.format("Error when continuing transaction(txId=%s), skip this transaction.",
                                                tx.getId()), e);
                        tx.error(String.format("Error when continuing: %s", e.getMessage()), e);
                    }
                }
            }
        }

    }

    private AgentTask loadAgentTask(Meta meta) {
        // 根据 type 去创建agentTask，这个和handler创建task是一个处理方法。
        String eventType = meta.getEventType();
        EventHandler handler = this.eventMapper.findHandler(eventType);

        if (handler == null) {
            throw new IllegalArgumentException("Handler of eventType(" + eventType + ") not found");
        }

        if (!(handler instanceof TaskSubmitionEventHandler)) {
            throw new IllegalArgumentException("Handler of eventType(" + eventType
                                               + ") is not instance of TaskSubmitionEventHandler, can not be submit.");
        }

        return ((TaskSubmitionEventHandler) handler).getTask(meta.getTaskParams());
    }

    @SuppressWarnings("unchecked")
    public long submit(AgentTask task) throws TaskSubmitException {
        try {
            TaskTransaction transaction = this.taskTransactionManager.createTransaction(task);
            this.logTransfer.submit(transaction);

            return execTransaction(transaction);
        } catch (TaskTransactionCreationException e) {
            log.error(String.format("Fail to submit task. (AgentTask's type=%s)", task.getClass().getName()), e);
            throw new TaskSubmitException(e);
        }
    }

    private long execTransaction(final TaskTransaction<AgentTask> transaction) throws TaskSubmitException {
        try {
            final long txId = transaction.getId();
            this.runningTransactions.put(txId, transaction);

            this.workerPool.submit(new Callable<Void>() {

                public Void call() {
                    try {
                        transaction.execute();
                    } finally {
                        DefaultTaskExecutionContainer.this.runningTransactions.remove(txId);
                    }
                    return null;
                }
            });

            return txId;

        } catch (Throwable e) {
            log.error(String.format("Fail to submit task. (AgentTask's type=%s)",
                                    transaction.getTask().getClass().getName()), e);
            throw new TaskSubmitException(e);
        }
    }

    public TaskStatus checkStatus(long transactionId) throws TaskNotFoundException {
        TaskTransaction transaction = findTransaction(transactionId);
        return new TaskStatus(transaction.getMeta().getStatus(), transaction.getMeta().getDuration(),
                              transaction.getMeta().getReturnValue());
    }

    private TaskTransaction findTransaction(long transactionId) throws TaskNotFoundException {
        TaskTransaction tx = this.runningTransactions.get(transactionId);
        if (tx != null) {
            return tx;
        } else {
            return this.taskTransactionManager.getTransaction(transactionId);
        }
    }

    public void cancel(long transactionId) throws TaskNotFoundException {
        TaskTransaction tx = this.runningTransactions.get(transactionId);
        if (tx == null) {
            throw new TaskNotFoundException(String.format("Task with transaction id {%s} not found or not running",
                                                          String.valueOf(transactionId)));
        } else {
            tx.cancel();
            this.runningTransactions.remove(transactionId);
        }
    }

}