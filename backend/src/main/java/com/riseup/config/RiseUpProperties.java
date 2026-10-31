package com.riseup.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "riseup")
public class RiseUpProperties {
    private String version = "0.2.0";
    private String environment = "development";
    private String demoToken = "dev-token";
    private boolean requireAuth;
    private boolean enableScheduler = true;
    private String publicBaseUrl = "http://localhost:8000";
    private String secretKey = "change-me";
    private int verificationTimeoutSeconds = 120;
    private int snoozeMinutes = 10;
    private int maxSnoozes = 3;
    private int checkinResponseMinutes = 5;
    private int escalationResponseSeconds = 30;
    private int emergencyContactAttempts = 3;
    private int reminderEscalationMinutes = 5;
    private int reminderFinalMinutes = 15;
    private int reminderMissedMinutes = 25;
    private int reminderSnoozeMinutes = 15;
    private int reminderRetryMinutes = 2;
    private int learningCronHour = 3;
    private boolean cameraAvailable = true;
    private int maxAlarmsPerUser = 20;
    private String openaiApiKey = "";
    private String claudeApiKey = "";
    private String claudeModel = "claude-sonnet-4-5";
    private String openaiModel = "gpt-4o";
    private String llmEscalationProvider = "claude";
    private String llmParseProvider = "openai";
    private String llmPrioritizeProvider = "claude";
    private String llmRecommendProvider = "claude";
    private String mockLlmMode = "normal";
    private String twilioAccountSid = "";
    private String twilioAuthToken = "";
    private String twilioPhoneNumber = "";
    private String alexaApiKey = "";
    private String alexaNotifyUrl = "https://api.notifymyecho.com/v1/NotifyMe";
    private String firebaseApiKey = "";
    private String smtpHost = "";
    private int smtpPort = 587;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private String smtpFrom = "";
    private boolean smtpUseTls = true;
    private String mockToolFailures = "";
    private String redisUrl = "";
    private String corsOrigins = "http://localhost:5173,http://localhost:3000";

    public static String configured(String value) {
        if (value == null) return null;
        String text = value.strip();
        if (text.isEmpty()) return null;
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.startsWith("YOUR_") || upper.endsWith("_HERE") || "CHANGE-ME".equals(upper)) {
            return null;
        }
        return text;
    }

    public Set<String> mockToolFailureSet() {
        return Arrays.stream(mockToolFailures.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getDemoToken() { return demoToken; }
    public void setDemoToken(String demoToken) { this.demoToken = demoToken; }
    public boolean isRequireAuth() { return requireAuth; }
    public void setRequireAuth(boolean requireAuth) { this.requireAuth = requireAuth; }
    public boolean isEnableScheduler() { return enableScheduler; }
    public void setEnableScheduler(boolean enableScheduler) { this.enableScheduler = enableScheduler; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public int getVerificationTimeoutSeconds() { return verificationTimeoutSeconds; }
    public void setVerificationTimeoutSeconds(int verificationTimeoutSeconds) { this.verificationTimeoutSeconds = verificationTimeoutSeconds; }
    public int getSnoozeMinutes() { return snoozeMinutes; }
    public void setSnoozeMinutes(int snoozeMinutes) { this.snoozeMinutes = snoozeMinutes; }
    public int getMaxSnoozes() { return maxSnoozes; }
    public void setMaxSnoozes(int maxSnoozes) { this.maxSnoozes = maxSnoozes; }
    public int getCheckinResponseMinutes() { return checkinResponseMinutes; }
    public void setCheckinResponseMinutes(int checkinResponseMinutes) { this.checkinResponseMinutes = checkinResponseMinutes; }
    public int getEscalationResponseSeconds() { return escalationResponseSeconds; }
    public void setEscalationResponseSeconds(int escalationResponseSeconds) { this.escalationResponseSeconds = escalationResponseSeconds; }
    public int getEmergencyContactAttempts() { return emergencyContactAttempts; }
    public void setEmergencyContactAttempts(int emergencyContactAttempts) { this.emergencyContactAttempts = emergencyContactAttempts; }
    public int getReminderEscalationMinutes() { return reminderEscalationMinutes; }
    public void setReminderEscalationMinutes(int reminderEscalationMinutes) { this.reminderEscalationMinutes = reminderEscalationMinutes; }
    public int getReminderFinalMinutes() { return reminderFinalMinutes; }
    public void setReminderFinalMinutes(int reminderFinalMinutes) { this.reminderFinalMinutes = reminderFinalMinutes; }
    public int getReminderMissedMinutes() { return reminderMissedMinutes; }
    public void setReminderMissedMinutes(int reminderMissedMinutes) { this.reminderMissedMinutes = reminderMissedMinutes; }
    public int getReminderSnoozeMinutes() { return reminderSnoozeMinutes; }
    public void setReminderSnoozeMinutes(int reminderSnoozeMinutes) { this.reminderSnoozeMinutes = reminderSnoozeMinutes; }
    public int getReminderRetryMinutes() { return reminderRetryMinutes; }
    public void setReminderRetryMinutes(int reminderRetryMinutes) { this.reminderRetryMinutes = reminderRetryMinutes; }
    public int getLearningCronHour() { return learningCronHour; }
    public void setLearningCronHour(int learningCronHour) { this.learningCronHour = learningCronHour; }
    public boolean isCameraAvailable() { return cameraAvailable; }
    public void setCameraAvailable(boolean cameraAvailable) { this.cameraAvailable = cameraAvailable; }
    public int getMaxAlarmsPerUser() { return maxAlarmsPerUser; }
    public void setMaxAlarmsPerUser(int maxAlarmsPerUser) { this.maxAlarmsPerUser = maxAlarmsPerUser; }
    public String getOpenaiApiKey() { return openaiApiKey; }
    public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }
    public String getClaudeApiKey() { return claudeApiKey; }
    public void setClaudeApiKey(String claudeApiKey) { this.claudeApiKey = claudeApiKey; }
    public String getClaudeModel() { return claudeModel; }
    public void setClaudeModel(String claudeModel) { this.claudeModel = claudeModel; }
    public String getOpenaiModel() { return openaiModel; }
    public void setOpenaiModel(String openaiModel) { this.openaiModel = openaiModel; }
    public String getLlmEscalationProvider() { return llmEscalationProvider; }
    public void setLlmEscalationProvider(String llmEscalationProvider) { this.llmEscalationProvider = llmEscalationProvider; }
    public String getLlmParseProvider() { return llmParseProvider; }
    public void setLlmParseProvider(String llmParseProvider) { this.llmParseProvider = llmParseProvider; }
    public String getLlmPrioritizeProvider() { return llmPrioritizeProvider; }
    public void setLlmPrioritizeProvider(String llmPrioritizeProvider) { this.llmPrioritizeProvider = llmPrioritizeProvider; }
    public String getLlmRecommendProvider() { return llmRecommendProvider; }
    public void setLlmRecommendProvider(String llmRecommendProvider) { this.llmRecommendProvider = llmRecommendProvider; }
    public String getMockLlmMode() { return mockLlmMode; }
    public void setMockLlmMode(String mockLlmMode) { this.mockLlmMode = mockLlmMode; }
    public String getTwilioAccountSid() { return twilioAccountSid; }
    public void setTwilioAccountSid(String twilioAccountSid) { this.twilioAccountSid = twilioAccountSid; }
    public String getTwilioAuthToken() { return twilioAuthToken; }
    public void setTwilioAuthToken(String twilioAuthToken) { this.twilioAuthToken = twilioAuthToken; }
    public String getTwilioPhoneNumber() { return twilioPhoneNumber; }
    public void setTwilioPhoneNumber(String twilioPhoneNumber) { this.twilioPhoneNumber = twilioPhoneNumber; }
    public String getAlexaApiKey() { return alexaApiKey; }
    public void setAlexaApiKey(String alexaApiKey) { this.alexaApiKey = alexaApiKey; }
    public String getAlexaNotifyUrl() { return alexaNotifyUrl; }
    public void setAlexaNotifyUrl(String alexaNotifyUrl) { this.alexaNotifyUrl = alexaNotifyUrl; }
    public String getFirebaseApiKey() { return firebaseApiKey; }
    public void setFirebaseApiKey(String firebaseApiKey) { this.firebaseApiKey = firebaseApiKey; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }
    public String getSmtpFrom() { return smtpFrom; }
    public void setSmtpFrom(String smtpFrom) { this.smtpFrom = smtpFrom; }
    public boolean isSmtpUseTls() { return smtpUseTls; }
    public void setSmtpUseTls(boolean smtpUseTls) { this.smtpUseTls = smtpUseTls; }
    public String getMockToolFailures() { return mockToolFailures; }
    public void setMockToolFailures(String mockToolFailures) { this.mockToolFailures = mockToolFailures; }
    public String getRedisUrl() { return redisUrl; }
    public void setRedisUrl(String redisUrl) { this.redisUrl = redisUrl; }
    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
}
