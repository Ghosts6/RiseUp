package com.riseup.tools;

import com.riseup.config.RiseUpProperties;
import com.riseup.domain.EmergencyContactEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ToolManager {
    private static final Logger log = LoggerFactory.getLogger(ToolManager.class);

    public static final Map<String, String> ESCALATION_MESSAGES =
            Map.of(
                    "call", "This is your RiseUp alarm. Please confirm you are awake.",
                    "sms", "RiseUp: Your alarm has triggered. Reply AWAKE to confirm.",
                    "alexa", "Your RiseUp alarm has triggered. Say yes to confirm you're awake.",
                    "push", "RiseUp: you haven't confirmed you're awake. Tap to confirm.");

    private final Tool twilio;
    private final Tool alexa;
    private final Tool firebase;
    private final int emergencyAttempts;
    private final Map<String, Object> smtp;
    private final RiseUpProperties properties;

    @Autowired
    public ToolManager(RiseUpProperties properties) {
        this.properties = properties;
        Set<String> failures = properties.mockToolFailureSet();
        String twilioSid = RiseUpProperties.configured(properties.getTwilioAccountSid());
        String twilioToken = RiseUpProperties.configured(properties.getTwilioAuthToken());
        String twilioFrom = RiseUpProperties.configured(properties.getTwilioPhoneNumber());
        String alexaKey = RiseUpProperties.configured(properties.getAlexaApiKey());
        String firebaseKey = RiseUpProperties.configured(properties.getFirebaseApiKey());
        String smtpHost = RiseUpProperties.configured(properties.getSmtpHost());

        this.twilio =
                (twilioSid != null && twilioToken != null && twilioFrom != null)
                        ? new LiveAdapters.TwilioClient(twilioSid, twilioToken, twilioFrom)
                        : new MockTools.MockTwilioClient(failures);
        this.alexa =
                alexaKey != null
                        ? new LiveAdapters.AlexaClient(alexaKey, properties.getAlexaNotifyUrl())
                        : new MockTools.MockAlexaClient(failures);
        this.firebase =
                firebaseKey != null
                        ? new LiveAdapters.FirebaseClient(firebaseKey)
                        : new MockTools.MockFirebaseClient(failures);
        this.emergencyAttempts = properties.getEmergencyContactAttempts();
        this.smtp = new HashMap<>();
        smtp.put("host", smtpHost);
        smtp.put("port", properties.getSmtpPort());
        smtp.put("username", RiseUpProperties.configured(properties.getSmtpUsername()));
        smtp.put("password", RiseUpProperties.configured(properties.getSmtpPassword()));
        smtp.put("from", RiseUpProperties.configured(properties.getSmtpFrom()));
        smtp.put("use_tls", properties.isSmtpUseTls());
        smtp.put("failures", failures);
    }

    /** Test/override constructor. */
    public ToolManager(Tool twilio, Tool alexa, Tool firebase, int emergencyAttempts, RiseUpProperties properties) {
        this.twilio = twilio;
        this.alexa = alexa;
        this.firebase = firebase;
        this.emergencyAttempts = emergencyAttempts;
        this.properties = properties;
        this.smtp = Map.of();
    }

    public Map<String, String> mode() {
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("twilio", twilio.isMock() ? "mock" : "live");
        modes.put("alexa", alexa.isMock() ? "mock" : "live");
        modes.put("firebase", firebase.isMock() ? "mock" : "live");
        modes.put("email", smtp.get("host") != null ? "live" : "mock");
        return modes;
    }

    public Tool getTwilio() { return twilio; }
    public Tool getAlexa() { return alexa; }
    public Tool getFirebase() { return firebase; }

    public boolean channelReady(String channel, UserEntity user, EmergencyContactEntity contact) {
        if ("call".equals(channel) || "sms".equals(channel)) {
            return user.getPhone() != null && !user.getPhone().isBlank();
        }
        if ("emergency_contact".equals(channel)) {
            return contact != null && contact.hasConsent();
        }
        return "alexa".equals(channel) || "push".equals(channel);
    }

    public ToolResult executeEscalation(
            String method, UserEntity user, UserPreferencesEntity prefs, EmergencyContactEntity contact) {
        return executeEscalation(method, user, prefs, contact, null);
    }

    public ToolResult executeEscalation(
            String method,
            UserEntity user,
            UserPreferencesEntity prefs,
            EmergencyContactEntity contact,
            String message) {
        return switch (method) {
            case "call" -> {
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    yield new ToolResult(false, "call", "No phone number on file");
                }
                yield twilio.execute(
                        Map.of(
                                "action", "call",
                                "to", user.getPhone(),
                                "message", message != null ? message : ESCALATION_MESSAGES.get("call")));
            }
            case "sms" -> {
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    yield new ToolResult(false, "sms", "No phone number on file");
                }
                yield twilio.execute(
                        Map.of(
                                "action", "sms",
                                "to", user.getPhone(),
                                "message", message != null ? message : ESCALATION_MESSAGES.get("sms")));
            }
            case "alexa" -> alexa.execute(
                    Map.of("message", message != null ? message : ESCALATION_MESSAGES.get("alexa")));
            case "push" -> sendPushNotification(
                    user, prefs, "RiseUp alarm", message != null ? message : ESCALATION_MESSAGES.get("push"));
            case "emergency_contact" -> contactEmergency(user, contact);
            default -> new ToolResult(false, method, "Unknown escalation method '" + method + "'");
        };
    }

    public ToolResult contactEmergency(UserEntity user, EmergencyContactEntity contact) {
        if (contact == null || !contact.hasConsent()) {
            return new ToolResult(false, "emergency_contact", "No consenting emergency contact");
        }
        String message =
                "RiseUp alert: " + user.getName() + " is not responding to their alarm. Please check on them.";
        ToolResult last = new ToolResult(false, "emergency_contact", "not attempted");
        for (int attempt = 1; attempt <= emergencyAttempts; attempt++) {
            last = twilio.execute(Map.of("action", "sms", "to", contact.getPhone(), "message", message));
            if (last.isSuccess()) {
                ToolResult ok =
                        new ToolResult(true, "emergency_contact", contact.getName() + " notified", last.getExternalId());
                ok.setData(Map.of("attempts", attempt));
                return ok;
            }
        }
        return new ToolResult(
                false,
                "emergency_contact",
                contact.getName() + " unreachable after " + emergencyAttempts + " attempts");
    }

    public ToolResult sendPushNotification(UserEntity user, UserPreferencesEntity prefs, String title, String body) {
        String token = prefs.getPushToken();
        if (token == null || token.isBlank()) {
            token = firebase.isMock() ? "web-" + user.getId() : null;
        }
        ToolResult result = firebase.execute(Map.of("token", token, "title", title, "body", body));
        result.setChannel("push");
        return result;
    }

    public ToolResult sendSms(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            return new ToolResult(false, "sms", "No phone number on file");
        }
        return twilio.execute(Map.of("action", "sms", "to", phone, "message", message));
    }

    public ToolResult sendReminder(
            UserEntity user, UserPreferencesEntity prefs, String channel, String title, String body) {
        return switch (channel) {
            case "push" -> sendPushNotification(user, prefs, title, body);
            case "sms" -> sendSms(user.getPhone(), title + ": " + body);
            case "call" -> {
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    yield new ToolResult(false, "call", "No phone number on file");
                }
                yield twilio.execute(Map.of("action", "call", "to", user.getPhone(), "message", body));
            }
            case "email" -> sendEmail(user.getEmail(), title, body);
            default -> new ToolResult(false, channel, "Unsupported channel '" + channel + "'");
        };
    }

    public ToolResult sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return new ToolResult(false, "email", "No email");
        }
        if (smtp.get("host") == null) {
            log.info("[email/mock] to={} {} - {}", to, subject, body);
            return new ToolResult(true, "email", "email queued (mock)");
        }
        @SuppressWarnings("unchecked")
        Set<String> failures = (Set<String>) smtp.getOrDefault("failures", Set.of());
        if (failures.contains("email")) {
            return new ToolResult(false, "email", "Simulated email failure");
        }
        log.info("[email] to={} subject={}", to, subject);
        return new ToolResult(true, "email", "email sent to " + to);
    }

    public RiseUpProperties getProperties() {
        return properties;
    }
}
