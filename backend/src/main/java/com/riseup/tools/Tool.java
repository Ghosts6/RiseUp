package com.riseup.tools;

import java.util.Map;

/** Target interface the domain uses; vendors are adapted behind it. */
public interface Tool {
    String getName();

    default boolean isMock() {
        return false;
    }

    ToolResult execute(Map<String, Object> payload);
}
