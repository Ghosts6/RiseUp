package com.riseup.ai;

import com.riseup.config.RiseUpProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LLMClient {
    private final Map<String, LLMProvider> providers;
    private final Map<String, String> routing;
    private final int maxRetries;
    private final MockProvider mock;

    @Autowired
    public LLMClient(RiseUpProperties properties) {
        this.mock = new MockProvider(properties.getMockLlmMode());
        this.providers = new LinkedHashMap<>();
        providers.put("mock", mock);
        String claudeKey = RiseUpProperties.configured(properties.getClaudeApiKey());
        String openaiKey = RiseUpProperties.configured(properties.getOpenaiApiKey());
        if (claudeKey != null) {
            providers.put("claude", new HttpProviders.ClaudeProvider(claudeKey, properties.getClaudeModel()));
        }
        if (openaiKey != null) {
            providers.put("openai", new HttpProviders.OpenAIProvider(openaiKey, properties.getOpenaiModel()));
        }
        this.routing = new LinkedHashMap<>();
        routing.put("escalation", properties.getLlmEscalationProvider());
        routing.put("parse", properties.getLlmParseProvider());
        routing.put("prioritize", properties.getLlmPrioritizeProvider());
        routing.put("recommend", properties.getLlmRecommendProvider());
        routing.put("chat", properties.getLlmChatProvider());
        routing.put("vision", properties.getLlmVisionProvider());
        this.maxRetries = 1;
    }

    public LLMClient(Map<String, LLMProvider> providers, Map<String, String> routing, int maxRetries) {
        this.providers = providers;
        this.routing = routing;
        this.maxRetries = maxRetries;
        this.mock = (MockProvider) providers.getOrDefault("mock", new MockProvider("normal"));
    }

    public Map<String, String> getRouting() {
        return routing;
    }

    public MockProvider getMock() {
        return mock;
    }

    public LLMProvider providerFor(String task) {
        String requested = routing.getOrDefault(task, "mock").toLowerCase(Locale.ROOT);
        if ("mock".equals(requested)) return providers.get("mock");
        LLMProvider p = providers.get(requested);
        return p != null ? p : providers.get("mock");
    }

    public LLMResponse sendRequest(String prompt, String task, String system) {
        LLMProvider provider = providerFor(task);
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                String text = provider.complete(prompt, system);
                return new LLMResponse(text, "llm:" + provider.getName(), provider.getModel());
            } catch (Exception e) {
                last = e;
            }
        }
        throw new LLMProviderError("LLM unavailable: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    /** Image + prompt on the "vision" route; throws when that provider cannot read images. */
    public LLMResponse sendVision(String prompt, String system, byte[] image, String mediaType) {
        LLMProvider provider = providerFor("vision");
        if (!provider.supportsVision()) {
            throw new LLMProviderError("No vision-capable LLM configured (" + provider.label() + ")");
        }
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                String text = provider.completeWithImage(prompt, system, image, mediaType);
                return new LLMResponse(text, "llm:" + provider.getName(), provider.getModel());
            } catch (Exception e) {
                last = e;
            }
        }
        throw new LLMProviderError("LLM vision unavailable: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    public LLMResponse sendChat(java.util.List<java.util.Map<String, String>> messages, String system) {
        LLMProvider provider = providerFor("chat");
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                String text = provider.completeChat(messages, system);
                return new LLMResponse(text, "llm:" + provider.getName(), provider.getModel());
            } catch (Exception e) {
                last = e;
            }
        }
        throw new LLMProviderError("LLM chat unavailable: " + (last == null ? "unknown" : last.getMessage()), last);
    }
}
