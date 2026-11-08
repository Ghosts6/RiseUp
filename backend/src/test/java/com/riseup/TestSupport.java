package com.riseup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.clock.FixedClock;
import com.riseup.domain.AlarmEntity;
import com.riseup.repository.AlarmRepository;
import com.riseup.scheduling.JobScheduler;
import com.riseup.scheduling.ManualJobScheduler;
import com.riseup.verification.MathTask;
import com.riseup.verification.TriviaTask;
import com.riseup.verification.VerificationTaskFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.TestClockConfig.class)
public abstract class TestSupport {
    public static final LocalDateTime START = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected FixedClock clock;
    @Autowired protected JobScheduler scheduler;
    @Autowired protected AlarmRepository alarmRepository;
    @Autowired protected VerificationTaskFactory taskFactory;

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        FixedClock fixedClock() {
            return new FixedClock(START);
        }

        @Bean
        @Primary
        ManualJobScheduler manualJobScheduler() {
            return new ManualJobScheduler();
        }
    }

    protected ManualJobScheduler manual() {
        return (ManualJobScheduler) scheduler;
    }

    protected void advance(long amount, ChronoUnit unit) {
        clock.advance(amount, unit);
        manual().runDue(clock.now());
    }

    protected void advanceMinutes(long minutes) {
        advance(minutes, ChronoUnit.MINUTES);
    }

    protected void advanceSeconds(long seconds) {
        advance(seconds, ChronoUnit.SECONDS);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode createAlarm(Map<String, Object> body) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("time", "07:00");
        payload.put("label", "Work");
        payload.put("verification_task_type", "math");
        payload.putAll(body);
        MvcResult result =
                mvc.perform(
                                post("/api/alarms")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsString(payload)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json(result);
    }

    protected JsonNode createAlarm() throws Exception {
        return createAlarm(Map.of());
    }

    protected Map<String, Object> solveChallenge(int alarmId) {
        AlarmEntity alarm = alarmRepository.findById(alarmId).orElseThrow();
        Map<String, Object> state = alarm.getCurrentTask();
        String kind = String.valueOf(state.get("type"));
        return switch (kind) {
            case "math" -> {
                MathTask task = (MathTask) taskFactory.fromState(state);
                yield Map.of("answer", String.valueOf(task.answer()));
            }
            case "trivia" -> {
                int index = ((Number) state.get("index")).intValue();
                yield Map.of("answer", String.valueOf(TriviaTask.TRIVIA_BANK.get(index).correct()));
            }
            case "custom" -> Map.of("completed", true);
            default -> throw new AssertionError("cannot solve " + kind);
        };
    }

    protected Map<String, Object> wrongAnswer(int alarmId) {
        int correct = Integer.parseInt(String.valueOf(solveChallenge(alarmId).get("answer")));
        return Map.of("answer", String.valueOf(correct + 1));
    }

    protected void ringAndTimeout(int alarmId) throws Exception {
        mvc.perform(post("/api/alarms/" + alarmId + "/trigger")).andExpect(status().isOk());
        advanceSeconds(121);
    }

    protected JsonNode getJson(String path) throws Exception {
        return json(mvc.perform(MockMvcRequestBuilders.get(path)).andExpect(status().isOk()).andReturn());
    }
}
