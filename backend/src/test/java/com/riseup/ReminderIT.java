package com.riseup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void parseAndCreateReminder() throws Exception {
        mockMvc.perform(post("/api/reminders/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content(mapper.writeValueAsString(Map.of("text", "Remind me to call mom tomorrow at 9am"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());

        String due = LocalDateTime.now(ZoneOffset.UTC).plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content(mapper.writeValueAsString(Map.of(
                                "title", "Call mom",
                                "due", due,
                                "priority", "high",
                                "category", "personal",
                                "repeat_pattern", "none",
                                "notification_type", "push"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Call mom"));

        mockMvc.perform(get("/api/reminders").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }
}
