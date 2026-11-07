package com.riseup.reminder;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMProviderError;
import com.riseup.ai.LLMResponse;
import com.riseup.ai.MockNlp;
import com.riseup.ai.PromptBuilder;
import com.riseup.ai.ResponseParser;
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
    private final ResponseParser parser = new ResponseParser();

    public NlParser(LLMClient llm) {
        this.llm = llm;
    }

    public Map<String, Object> parse(String text, UserEntity user, LocalDateTime nowUtc) {
        LocalDateTime local = TimeUtil.toLocal(nowUtc, user.getTimezone());
        String localIso = local.toString();
        if ("mock".equals(llm.providerFor("parse").getName())) {
            return withMeta(MockNlp.parseReminderText(text, localIso), "mock", false);
        }

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("text", text);
        inputs.put("now_local", localIso);
        inputs.put("timezone", user.getTimezone());

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                LLMResponse resp = llm.sendRequest(prompts.buildParsePrompt(inputs), "parse", prompts.system);
                Map<String, Object> parsed = parser.parseReminder(resp.text());
                return withMeta(parsed, resp.decidedBy(), false);
            } catch (ResponseParser.ParseError | LLMProviderError e) {
                last = e;
            } catch (Exception e) {
                last = e;
                break;
            }
        }
        Map<String, Object> fallback = MockNlp.parseReminderText(text, localIso);
        Map<String, Object> out = withMeta(fallback, "rules", true);
        if (last != null) {
            out.put("fallback_reason", last.getMessage());
        }
        return out;
    }

    private static Map<String, Object> withMeta(Map<String, Object> parsed, String decidedBy, boolean fallbackUsed) {
        Map<String, Object> out = new LinkedHashMap<>(parsed);
        out.put("decided_by", decidedBy);
        out.put("fallback_used", fallbackUsed);
        return out;
    }
}
