package com.riseup.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.api.dto.Dtos;
import com.riseup.domain.EmergencyContactEntity;
import com.riseup.prefs.PreferencesService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PreferencesController {
    private final PreferencesService preferences;
    private final AuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();

    public PreferencesController(PreferencesService preferences, AuthService auth) {
        this.preferences = preferences;
        this.auth = auth;
    }

    @GetMapping("/api/prefs")
    public Map<String, Object> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return preferences.get(auth.resolveUserId(authorization, token));
    }

    @PatchMapping("/api/prefs")
    public Map<String, Object> update(
            @RequestBody Dtos.PreferencesIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.convertValue(body, Map.class);
        map.entrySet().removeIf(e -> e.getValue() == null);
        return preferences.update(auth.resolveUserId(authorization, token), map);
    }

    @PostMapping("/api/prefs/test")
    public Map<String, Object> test(
            @RequestBody Dtos.TestChannelIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return preferences.testChannel(auth.resolveUserId(authorization, token), body.channel);
    }

    @GetMapping("/api/contacts")
    public List<Map<String, Object>> listContacts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        return preferences.get(userId).get("contacts") instanceof List<?> l
                ? l.stream().map(o -> (Map<String, Object>) o).toList()
                : List.of();
    }

    @PostMapping("/api/contacts")
    public ResponseEntity<Map<String, Object>> addContact(
            @RequestBody Dtos.ContactIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        EmergencyContactEntity c = preferences.addContact(userId, body.name, body.phone);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiMapper.contact(c, preferences.consentLink(c)));
    }

    @DeleteMapping("/api/contacts/{contactId}")
    public ResponseEntity<Void> remove(
            @PathVariable int contactId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        preferences.removeContact(auth.resolveUserId(authorization, token), contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/contacts/{contactId}/test")
    public Map<String, Object> testContact(
            @PathVariable int contactId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return preferences.testContact(auth.resolveUserId(authorization, token), contactId);
    }

    @GetMapping(value = "/api/contacts/consent/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public String consent(@PathVariable String token, @RequestParam(defaultValue = "yes") String answer) {
        boolean accept = SetOf(answer.toLowerCase(Locale.ROOT), "yes", "y", "accept", "true");
        EmergencyContactEntity contact = preferences.respondConsent(token, accept);
        String verdict = accept ? "You are now a RiseUp emergency contact." : "You declined. Thanks!";
        return "<html><body style='font-family:sans-serif'><h2>Hi "
                + escape(contact.getName())
                + "</h2><p>"
                + verdict
                + "</p></body></html>";
    }

    private static boolean SetOf(String v, String... opts) {
        for (String o : opts) if (o.equals(v)) return true;
        return false;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
