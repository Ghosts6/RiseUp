package com.riseup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PrefsAndInsightsTest extends TestSupport {
    @Test
    void updatePrefsAndAddContact() throws Exception {
        JsonNode prefs =
                json(
                        mvc.perform(
                                        patch("/api/prefs")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"checkin_delay_minutes\":8,\"enabled_channels\":[\"sms\",\"push\"]}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(prefs.get("preferences").get("checkin_delay_minutes").asInt()).isEqualTo(8);

        JsonNode contact =
                json(
                        mvc.perform(
                                        post("/api/contacts")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"name\":\"Alex\",\"phone\":\"+15555550999\"}"))
                                .andExpect(status().isCreated())
                                .andReturn());
        assertThat(contact.get("consent_status").asText()).isEqualTo("pending");
        assertThat(contact.get("consent_link").asText()).contains("/api/contacts/consent/");
    }

    @Test
    void contactPhoneIsValidatedAndNormalized() throws Exception {
        mvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"phone\":\"123\"}"))
                .andExpect(status().is4xxClientError());
        JsonNode ok = json(mvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sam\",\"phone\":\"+1 (416) 555-0123\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(ok.get("phone").asText()).isEqualTo("+14165550123");
    }

    @Test
    void generateRecommendationsAndStats() throws Exception {
        JsonNode generated =
                json(mvc.perform(post("/api/recommendations/generate")).andExpect(status().isOk()).andReturn());
        assertThat(generated.get("items").isArray()).isTrue();
        assertThat(generated.get("items").size()).isGreaterThan(0);

        JsonNode stats = getJson("/api/stats?range=30d");
        assertThat(stats.get("range_days").asInt()).isEqualTo(30);
        assertThat(stats.has("alarms_triggered")).isTrue();

        JsonNode profile = json(mvc.perform(post("/api/profile/recompute")).andExpect(status().isOk()).andReturn());
        assertThat(profile.has("verification_stats")).isTrue();
    }

    @Test
    void historyListsEventsAfterAlarmTrigger() throws Exception {
        JsonNode alarm = createAlarm();
        mvc.perform(post("/api/alarms/" + alarm.get("id").asInt() + "/trigger")).andExpect(status().isOk());
        JsonNode history = getJson("/api/history?type=alarm");
        assertThat(history.get("total").asInt()).isGreaterThan(0);
        assertThat(history.get("items").get(0).get("kind").asText()).isEqualTo("alarm");
    }
}
