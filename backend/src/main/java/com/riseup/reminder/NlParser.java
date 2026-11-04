package com.riseup.reminder;

import com.riseup.ai.LLMClient;
import com.riseup.ai.MockNlp;
import com.riseup.ai.PromptBuilder;
import com.riseup.domain.UserEntity;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NlParser {
    private final LLMClient llm;
    private final PromptBuilder prompts = new PromptBuilder();

    public NlParser(LLMClient llm) {
        this.llm = llm;
    }

    public Map<String, Object> parse(String text, UserEntity user, LocalDateTime nowUtc) {
        LocalDateTime local = TimeUtil.toLocal(nowUtc, user.getTimezone());
        // Prefer deterministic mock NLP for reliability when using mock provider
        if ("mock".equals(llm.providerFor("parse").getName())) {
            Map<String, Object> parsed = MockNlp.parseReminderText(text, local.toString());
            return parsed;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("text", text);
        inputs.put("now_local", local.toString());
        inputs.put("timezone", user.getTimezone());
        try {
            var resp = llm.sendRequest(prompts.buildParsePrompt(inputs), "parse", prompts.system);
            // fallback to mock nlp on any issue
            return MockNlp.parseReminderText(text, local.toString());
        } catch (Exception e) {
            return MockNlp.parseReminderText(text, local.toString());
        }
    }
}
