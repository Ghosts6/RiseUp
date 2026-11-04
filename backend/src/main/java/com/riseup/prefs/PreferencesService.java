package com.riseup.prefs;

import com.riseup.api.ApiMapper;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.EmergencyContactEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.error.NotFoundException;
import com.riseup.health.HealthService;
import com.riseup.repository.EmergencyContactRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserRepository;
import com.riseup.tools.ToolManager;
import com.riseup.tools.ToolResult;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService {
    private final UserRepository users;
    private final UserPreferencesRepository prefsRepo;
    private final EmergencyContactRepository contacts;
    private final ToolManager tools;
    private final RiseUpProperties settings;
    private final HealthService health;

    public PreferencesService(
            UserRepository users,
            UserPreferencesRepository prefsRepo,
            EmergencyContactRepository contacts,
            ToolManager tools,
            RiseUpProperties settings,
            HealthService health) {
        this.users = users;
        this.prefsRepo = prefsRepo;
        this.contacts = contacts;
        this.tools = tools;
        this.settings = settings;
        this.health = health;
    }

    public UserPreferencesEntity prefs(int userId) {
        return prefsRepo.findById(userId).orElseGet(() -> {
            UserPreferencesEntity p = new UserPreferencesEntity();
            p.setUserId(userId);
            return prefsRepo.save(p);
        });
    }

    public Map<String, Object> get(int userId) {
        UserEntity user = users.findById(userId).orElseThrow();
        UserPreferencesEntity preferences = prefs(userId);
        List<EmergencyContactEntity> contactList = contacts.findByUserIdOrderByIdAsc(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", ApiMapper.user(user));
        out.put("preferences", ApiMapper.preferences(preferences));
        out.put(
                "contacts",
                contactList.stream().map(c -> ApiMapper.contact(c, consentLink(c))).toList());
        out.put("warnings", List.of());
        Map<String, Object> healthPayload = health.payload();
        out.put("integrations", healthPayload.get("integrations"));
        out.put("llm", healthPayload.get("llm"));
        return out;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(int userId, Map<String, Object> body) {
        UserEntity user = users.findById(userId).orElseThrow();
        UserPreferencesEntity preferences = prefs(userId);
        if (body.get("enabled_channels") instanceof List<?> l) preferences.setEnabledChannels(l.stream().map(String::valueOf).toList());
        if (body.get("escalation_order") instanceof List<?> l) preferences.setEscalationOrder(l.stream().map(String::valueOf).toList());
        if (body.get("notification_methods") instanceof List<?> l)
            preferences.setNotificationMethods(l.stream().map(String::valueOf).toList());
        if (body.containsKey("quiet_hours_start"))
            preferences.setQuietHoursStart(body.get("quiet_hours_start") == null ? null : String.valueOf(body.get("quiet_hours_start")));
        if (body.containsKey("quiet_hours_end"))
            preferences.setQuietHoursEnd(body.get("quiet_hours_end") == null ? null : String.valueOf(body.get("quiet_hours_end")));
        if (body.get("checkin_enabled") instanceof Boolean b) preferences.setCheckinEnabled(b);
        if (body.get("checkin_delay_minutes") instanceof Number n) preferences.setCheckinDelayMinutes(n.intValue());
        if (body.get("default_task_type") != null) preferences.setDefaultTaskType(String.valueOf(body.get("default_task_type")));
        if (body.containsKey("push_token"))
            preferences.setPushToken(body.get("push_token") == null ? null : String.valueOf(body.get("push_token")));
        if (body.get("category_preferred_times") instanceof Map<?, ?> m)
            preferences.setCategoryPreferredTimes((Map<String, Object>) m);
        if (body.get("phone") != null) user.setPhone(String.valueOf(body.get("phone")));
        if (body.get("name") != null) user.setName(String.valueOf(body.get("name")));
        if (body.get("email") != null) user.setEmail(String.valueOf(body.get("email")));
        if (body.get("timezone") != null) user.setTimezone(String.valueOf(body.get("timezone")));
        users.save(user);
        prefsRepo.save(preferences);
        return get(userId);
    }

    public Map<String, Object> testChannel(int userId, String channel) {
        UserEntity user = users.findById(userId).orElseThrow();
        UserPreferencesEntity preferences = prefs(userId);
        ToolResult result =
                tools.sendReminder(user, preferences, channel, "RiseUp test", "This is a test notification.");
        return Map.of("success", result.isSuccess(), "channel", result.getChannel(), "detail", result.getDetail());
    }

    @Transactional
    public EmergencyContactEntity addContact(int userId, String name, String phone) {
        EmergencyContactEntity c = new EmergencyContactEntity();
        c.setUserId(userId);
        c.setName(name);
        c.setPhone(phone);
        c.setConsentStatus("pending");
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        c.setConsentToken(Base64.getUrlEncoder().withoutPadding().encodeToString(buf));
        return contacts.save(c);
    }

    @Transactional
    public void removeContact(int userId, int contactId) {
        EmergencyContactEntity c =
                contacts.findByIdAndUserId(contactId, userId)
                        .orElseThrow(() -> new NotFoundException("Contact " + contactId + " not found"));
        contacts.delete(c);
    }

    public Map<String, Object> testContact(int userId, int contactId) {
        UserEntity user = users.findById(userId).orElseThrow();
        EmergencyContactEntity c =
                contacts.findByIdAndUserId(contactId, userId)
                        .orElseThrow(() -> new NotFoundException("Contact " + contactId + " not found"));
        ToolResult result = tools.contactEmergency(user, c);
        return Map.of("success", result.isSuccess(), "detail", result.getDetail());
    }

    @Transactional
    public EmergencyContactEntity respondConsent(String token, boolean accept) {
        EmergencyContactEntity c =
                contacts.findByConsentToken(token)
                        .orElseThrow(() -> new NotFoundException("Consent link not found"));
        c.setConsentStatus(accept ? "granted" : "declined");
        return contacts.save(c);
    }

    public String consentLink(EmergencyContactEntity c) {
        return settings.getPublicBaseUrl() + "/api/contacts/consent/" + c.getConsentToken();
    }
}
