package com.riseup.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpProviders {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

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
                String messages =
                        system == null
                                ? "[{\"role\":\"user\",\"content\":" + json(prompt) + "}]"
                                : "[{\"role\":\"system\",\"content\":"
                                        + json(system)
                                        + "},{\"role\":\"user\",\"content\":"
                                        + json(prompt)
                                        + "}]";
                String body =
                        "{\"model\":"
                                + json(model)
                                + ",\"messages\":"
                                + messages
                                + ",\"temperature\":0.2,\"response_format\":{\"type\":\"json_object\"}}";
                HttpRequest req =
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                                .header("Authorization", "Bearer " + apiKey)
                                .header("content-type", "application/json")
                                .timeout(Duration.ofSeconds(20))
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    throw new LLMProviderError("OpenAI error " + resp.statusCode());
                }
                int i = resp.body().indexOf("\"content\":");
                int start = resp.body().indexOf('"', i + 10) + 1;
                int end = resp.body().indexOf('"', start);
                return resp.body().substring(start, end).replace("\\n", "\n");
            } catch (LLMProviderError e) {
                throw e;
            } catch (Exception e) {
                throw new LLMProviderError("OpenAI request failed: " + e.getMessage(), e);
            }
        }
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
