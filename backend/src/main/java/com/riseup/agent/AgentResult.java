package com.riseup.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentResult {
    private final String goal;
    private String status;
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private Object output;

    public AgentResult(String goal, String status) {
        this.goal = goal;
        this.status = status;
    }

    public String getGoal() { return goal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Map<String, Object>> getSteps() { return steps; }
    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }
}
