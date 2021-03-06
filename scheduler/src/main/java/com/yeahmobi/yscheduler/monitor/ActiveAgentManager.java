package com.yeahmobi.yscheduler.monitor;

import java.util.List;

import com.yeahmobi.yscheduler.model.Agent;

// agent 表会变成： id,name,teamId,group,ip,... (添加group字段，agent的enable保留后续用，active字段应该删除)
// group类似tag(不同team的tag可重复,比如一般都可以有叫做default的group)

// task 表，添加agentGroup。agentId/agentGroup 只填一个。页面可以指定在team内的agent和group选项里，选定一台agent或指定一个分组
public interface ActiveAgentManager {

    void heartbeat(long agentId);

    public boolean isActive(long agentId);

    List<Agent> getActiveList(long teamId);

    void checkAndUpdateAgentVersion(long agentId, String agentVersion);

}
