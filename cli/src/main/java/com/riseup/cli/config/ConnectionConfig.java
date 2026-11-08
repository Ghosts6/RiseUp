package com.riseup.cli.config;
public record ConnectionConfig(String server, String token) {
    public static final String DEFAULT_SERVER = "http://localhost:8000";
    public static final String DEFAULT_TOKEN = "dev-token";
    public static ConnectionConfig resolve(String serverFlag, String tokenFlag) {
        String server = first(serverFlag, System.getenv("RISEUP_SERVER"), DEFAULT_SERVER);
        String token = first(tokenFlag, System.getenv("RISEUP_TOKEN"), System.getenv("DEMO_TOKEN"), DEFAULT_TOKEN);
        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        return new ConnectionConfig(server, token);
    }
    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return "";
    }
}
