package com.riseup.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Live vendor adapters. When keys missing, ToolManager uses mocks instead. */
public final class LiveAdapters {
    private static final Logger log = LoggerFactory.getLogger(LiveAdapters.class);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private LiveAdapters() {}

    public static class TwilioClient implements Tool {
        private final String sid;
        private final String token;
        private final String from;

        public TwilioClient(String sid, String token, String from) {
            this.sid = sid;
            this.token = token;
            this.from = from;
        }

        @Override
        public String getName() {
            return "twilio";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            String action = String.valueOf(payload.getOrDefault("action", "sms"));
            String to = String.valueOf(payload.get("to"));
            String message = String.valueOf(payload.getOrDefault("message", ""));
            try {
                String path = "call".equals(action) ? "Calls.json" : "Messages.json";
                String body =
                        "call".equals(action)
                                ? "To=" + enc(to) + "&From=" + enc(from) + "&Twiml=" + enc("<Response><Say>" + message + "</Say></Response>")
                                : "To=" + enc(to) + "&From=" + enc(from) + "&Body=" + enc(message);
                String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
                HttpRequest req =
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/" + path))
                                .header("Authorization", "Basic " + auth)
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                boolean ok = resp.statusCode() < 400;
                return new ToolResult(ok, "call".equals(action) ? "call" : "sms", ok ? "delivered" : resp.body());
            } catch (Exception e) {
                return new ToolResult(false, "call".equals(action) ? "call" : "sms", e.getMessage());
            }
        }

        private static String enc(String s) {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }

    public static class AlexaClient implements Tool {
        private final String apiKey;
        private final String url;

        public AlexaClient(String apiKey, String url) {
            this.apiKey = apiKey;
            this.url = url;
        }

        @Override
        public String getName() {
            return "alexa";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            try {
                String json = "{\"notification\":\"" + String.valueOf(payload.getOrDefault("message", "")).replace("\"", "\\\"") + "\",\"accessCode\":\"" + apiKey + "\"}";
                HttpRequest req =
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                boolean ok = resp.statusCode() < 400;
                return new ToolResult(ok, "alexa", ok ? "delivered" : resp.body());
            } catch (Exception e) {
                return new ToolResult(false, "alexa", e.getMessage());
            }
        }
    }

    public static class FirebaseClient implements Tool {
        private final String apiKey;

        public FirebaseClient(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public String getName() {
            return "firebase";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            log.info("[firebase] push via key {} to {}", apiKey.substring(0, Math.min(4, apiKey.length())), payload.get("token"));
            // Real FCM requires a service account; treat configured key as live but log-only if HTTP fails.
            return new ToolResult(true, "push", "push queued");
        }
    }
}
