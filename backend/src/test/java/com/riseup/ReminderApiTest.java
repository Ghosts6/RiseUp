package com.riseup;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReminderApiTest extends TestSupport {
    @Test
    void createCompleteAndSnooze() throws Exception {
        JsonNode created =
                json(
                        mvc.perform(
                                        post("/api/reminders")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        mapper.writeValueAsString(
                                                                Map.of(
                                                                        "title",
                                                                        "Take meds",
                                                                        "due",
                                                                        "2026-10-19T12:00:00",
                                                                        "priority",
                                                                        "high",
                                                                        "category",
                                                                        "health"))))
                                .andExpect(status().isCreated())
                                .andReturn());
        assertThat(created.get("status").asText()).isEqualTo("scheduled");
        int id = created.get("id").asInt();

        JsonNode snoozed =
                json(
                        mvc.perform(
                                        post("/api/reminders/" + id + "/snooze")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"minutes\":15}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(snoozed.get("snooze_count").asInt()).isEqualTo(1);

        JsonNode completed =
                json(mvc.perform(post("/api/reminders/" + id + "/complete")).andExpect(status().isOk()).andReturn());
        assertThat(completed.get("status").asText()).isEqualTo("completed");
    }

    @Test
    void parseNaturalLanguage() throws Exception {
        JsonNode parsed =
                json(
                        mvc.perform(
                                        post("/api/reminders/parse")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"text\":\"remind me to call mom tomorrow at 9am\"}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(parsed.get("status").asText()).isEqualTo("ok");
        assertThat(parsed.get("draft").get("title").asText().toLowerCase()).contains("call");
    }

    @Test
    void priorityPreviewOrdersByPriority() throws Exception {
        mvc.perform(
                        post("/api/reminders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Low thing",
                                                        "due",
                                                        "2026-10-19T10:30:00Z",
                                                        "priority",
                                                        "low"))))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/reminders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Critical thing",
                                                        "due",
                                                        "2026-10-19T10:45:00Z",
                                                        "priority",
                                                        "critical"))))
                .andExpect(status().isCreated());
        JsonNode preview = getJson("/api/reminders/priority-preview");
        assertThat(preview.get("order").isEmpty()).isFalse();
        assertThat(preview.path("order").isArray()).isTrue();
        if (preview.path("order").size() > 0) {
            assertThat(preview.get("order").get(0).get("title").asText()).isEqualTo("Critical thing");
        }
    }

    @Test
    void deleteReminder() throws Exception {
        JsonNode created =
                json(
                        mvc.perform(
                                        post("/api/reminders")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        mapper.writeValueAsString(
                                                                Map.of("title", "Temp", "due", "2026-10-20T09:00:00"))))
                                .andExpect(status().isCreated())
                                .andReturn());
        mvc.perform(delete("/api/reminders/" + created.get("id").asInt())).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.get("/api/reminders/" + created.get("id").asInt())).andExpect(status().isNotFound());
    }
}
