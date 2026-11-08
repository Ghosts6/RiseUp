package com.riseup.alarm;

public interface AgentKick {
    void runEscalation(int alarmId, String episode, String trigger);
}
