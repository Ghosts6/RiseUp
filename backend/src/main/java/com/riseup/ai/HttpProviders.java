package com.riseup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class HttpProviders {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpProviders() {}

    public static class ClaudeProvider implements LLMProvider {
        private final String apiKey;
        private final String model;

        public ClaudeProvider(String apiKey, String model) {
            this.apiKey = apiKey;
            this.model = model;
        }

        @Override
        public String getName() {
            return "claude";
        }

        @Override
        public String getModel() {
            return model;
        }

        @Override
        public String complete(String prompt, String system) {
            try {
                String sys = system == null ? "" : ",\"system\":" + json(system);
                String body =
                        "{\"model\":"
                                + json(model)
                                + ",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":"
                                + json(prompt)
                                + "}]"
                                + sys
                                + "}";
                HttpRequest req =
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                                .header("x-api-key", apiKey)
                                .header("anthropic-version", "2023-06-01")
                                .header("content-type", "application/json")
                                .timeout(Duration.ofSeconds(20))
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    throw new LLMProviderError("Claude error " + resp.statusCode());
                }
                // naive extract
                int i = resp.body().indexOf("\"text\":");
                if (i < 0) return resp.body();
                int start = resp.body().indexOf('"', i + 7) + 1;
                int end = resp.body().indexOf('"', start);
                return resp.body().substring(start, end);
            } catch (LLMProviderError e) {
                throw e;
            } catch (Exception e) {
                throw new LLMProviderError("Claude request failed: " + e.getMessage(), e);
            }
        }
    }

    public static class OpenAIProvider implements LLMProvider {
        private final String apiKey;
        private final String model;

        public OpenAIProvider(String apiKey, String model) {
            this.apiKey = apiKey;
            this.model = model;
        }

        @Override
        public String getName() {
            return "openai";
        }

        @Override
        public String getModel() {
            return model;
        }

        @Override
        public String complete(String prompt, String system) {
            try {
                ObjectNode body = baseChatBody();
                // Keep temperature + JSON mode valid on gpt-6-luna Chat Completions
                body.put("reasoning_effort", "none");
                ArrayNode messages = body.putArray("messages");
                if (system != null && !system.isBlank()) {
                    messages.add(msg("system", system));
                }
                messages.add(msg("user", prompt == null ? "" : prompt));
                body.put("temperature", 0.2);
                body.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
                return postChat(body);
            } catch (LLMProviderError e) {
                throw e;
            } catch (Exception e) {
                throw new LLMProviderError("OpenAI request failed: " + e.getMessage(), e);
            }
        }

        @Override
        public String completeChat(List<Map<String, String>> turns, String system) {
            try {
                ObjectNode body = baseChatBody();
                // gpt-6-luna: keep Chat Completions compatible with temperature
                body.put("reasoning_effort", "none");
                body.put("temperature", 0.45);
                ArrayNode messages = body.putArray("messages");
                if (system != null && !system.isBlank()) {
                    messages.add(msg("system", system));
                }
                if (turns != null) {
                    for (Map<String, String> turn : turns) {
                        if (turn == null) {
                            continue;
                        }
                        String role = String.valueOf(turn.getOrDefault("role", "user"));
                        String content = String.valueOf(turn.getOrDefault("content", ""));
                        if (content.isBlank()) {
                            continue;
                        }
                        messages.add(msg(role, content));
                    }
                }
                return postChat(body);
            } catch (LLMProviderError e) {
                throw e;
            } catch (Exception e) {
                throw new LLMProviderError("OpenAI chat failed: " + e.getMessage(), e);
            }
        }

        private ObjectNode baseChatBody() {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            return body;
        }

        private String postChat(ObjectNode body) throws Exception {
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("content-type", "application/json")
                            .timeout(Duration.ofSeconds(45))
                            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                            .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new LLMProviderError("OpenAI error " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new LLMProviderError("OpenAI response missing content");
            }
            return content.asText();
        }

        private static ObjectNode msg(String role, String content) {
            ObjectNode n = MAPPER.createObjectNode();
            n.put("role", role);
            n.put("content", content);
            return n;
        }

        private static String truncate(String s) {
            if (s == null) {
                return "";
            }
            return s.length() > 240 ? s.substring(0, 240) : s;
        }
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
