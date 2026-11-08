package com.riseup.tools;

import java.util.HashMap;
import java.util.Map;

public class ToolResult {
    private boolean success;
    private String channel;
    private String detail = "";
    private String externalId;
    private Map<String, Object> data = new HashMap<>();

    public ToolResult() {}

    public ToolResult(boolean success, String channel, String detail) {
        this.success = success;
        this.channel = channel;
        this.detail = detail;
    }

    public ToolResult(boolean success, String channel, String detail, String externalId) {
        this(success, channel, detail);
        this.externalId = externalId;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
