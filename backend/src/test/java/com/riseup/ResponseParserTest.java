package com.riseup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riseup.ai.ResponseParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseParserTest {
    private final ResponseParser parser = new ResponseParser();

    @Test
    void parseReminderAcceptsEnvelopeAndRawDraft() {
        Map<String, Object> envelope =
                parser.parseReminder(
                        """
                        {"status":"ok","draft":{"title":"Call mom","due":"2026-11-07T09:00:00",
                        "priority":"high","category":"personal"}}
                        """);
        assertThat(envelope.get("status")).isEqualTo("ok");
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) envelope.get("draft");
        assertThat(draft.get("title")).isEqualTo("Call mom");
        assertThat(draft.get("priority")).isEqualTo("high");

        Map<String, Object> raw =
                parser.parseReminder(
                        "{\"title\":\"Gym\",\"due\":\"2026-11-07T07:30\",\"needs_clarification\":false}");
        assertThat(raw.get("status")).isEqualTo("ok");
    }

    @Test
    void parsePrioritizeRequiresPermutation() {
        Map<String, Object> ok =
                parser.parsePrioritize("{\"order\":[2,1],\"reasoning\":\"high first\"}", List.of(1, 2));
        assertThat(ok.get("order")).isEqualTo(List.of(2, 1));
        assertThatThrownBy(
                        () -> parser.parsePrioritize("{\"order\":[1],\"reasoning\":\"x\"}", List.of(1, 2)))
                .isInstanceOf(ResponseParser.ParseError.class);
    }

    @Test
    void parseRecommendationsReadsItems() {
        List<Map<String, Object>> items =
                parser.parseRecommendations(
                        """
                        {"items":[{"type":"enable_sms","title":"Add SMS","rationale":"fallback",
                        "change":{"add_channel":"sms"}}]}
                        """);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("title")).isEqualTo("Add SMS");
    }
}
