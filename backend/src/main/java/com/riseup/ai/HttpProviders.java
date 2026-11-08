package com.riseup.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class HttpProviders {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpProviders() {}

    /** Anthropic Messages API via the official Java SDK (Adapter over {@link LLMProvider}). */
    public static class ClaudeProvider implements LLMProvider {
        private static final long MAX_TOKENS = 4096L;

        private final AnthropicClient client;
        private final String model;

        public ClaudeProvider(String apiKey, String model) {
            this.client =
                    AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(Duration.ofSeconds(45))
                            .maxRetries(1)
                            .build();
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
            MessageCreateParams.Builder params = baseParams(system).addUserMessage(prompt == null ? "" : prompt);
            return send(params.build());
        }

        @Override
        public String completeChat(List<Map<String, String>> turns, String system) {
            MessageCreateParams.Builder params = baseParams(system);
            boolean any = false;
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
                    if ("assistant".equalsIgnoreCase(role)) {
                        // Messages API conversations must open with a user turn
                        if (any) {
                            params.addAssistantMessage(content);
                        }
                    } else if ("user".equalsIgnoreCase(role)) {
                        params.addUserMessage(content);
                        any = true;
                    }
                }
            }
            if (!any) {
                throw new LLMProviderError("Claude chat needs at least one user message");
            }
            return send(params.build());
        }

        @Override
        public boolean supportsVision() {
            return true;
        }

        @Override
        public String completeWithImage(String prompt, String system, byte[] image, String mediaType) {
            Base64ImageSource.MediaType type = "image/png".equals(mediaType)
                    ? Base64ImageSource.MediaType.IMAGE_PNG
                    : Base64ImageSource.MediaType.IMAGE_JPEG;
            ImageBlockParam img = ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(image))
                            .mediaType(type)
                            .build())
                    .build();
            MessageCreateParams params = baseParams(system)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(img),
                            ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build())))
                    .build();
            return send(params);
        }

        private MessageCreateParams.Builder baseParams(String system) {
            MessageCreateParams.Builder b =
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(MAX_TOKENS)
                            // Short structured tasks: keep thinking light for latency/cost
                            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
            if (system != null && !system.isBlank()) {
                b.system(system);
            }
            return b;
        }

        private String send(MessageCreateParams params) {
            Message response;
            try {
                response = client.messages().create(params);
            } catch (AnthropicServiceException e) {
                throw new LLMProviderError("Claude error " + e.statusCode() + ": " + truncate(e.getMessage()), e);
            } catch (AnthropicException e) {
                throw new LLMProviderError("Claude request failed: " + e.getMessage(), e);
            }
            String stop = response.stopReason().map(Object::toString).orElse("");
            if ("refusal".equals(stop)) {
                throw new LLMProviderError("Claude declined the request");
            }
            StringBuilder text = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(t -> text.append(t.text()));
            }
            if (text.isEmpty()) {
                throw new LLMProviderError("Claude response had no text (stop_reason=" + stop + ")");
            }
            return text.toString();
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
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 240 ? s.substring(0, 240) : s;
    }
}
