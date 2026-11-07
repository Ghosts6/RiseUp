package com.riseup.api;

import com.riseup.agent.AgentChatService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
public class AgentChatController {
    private final AgentChatService chat;
    private final AuthService auth;

    public AgentChatController(AgentChatService chat, AuthService auth) {
        this.chat = chat;
        this.auth = auth;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        int userId = auth.resolveUserId(authorization, token);
        String message = body == null ? "" : String.valueOf(body.getOrDefault("message", ""));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history =
                body != null && body.get("history") instanceof List<?> list
                        ? (List<Map<String, String>>) list
                        : List.of();
        try {
            return chat.chat(userId, message, history);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Agent unavailable: " + e.getMessage());
        }
    }

    @GetMapping("/briefing")
    public Map<String, Object> briefing(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        return chat.briefing(userId);
    }

    @PostMapping("/tools/execute")
    public Map<String, Object> executeTool(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        int userId = auth.resolveUserId(authorization, token);
        String tool = body == null ? "" : String.valueOf(body.getOrDefault("tool", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> args =
                body != null && body.get("args") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
        try {
            return chat.executeTool(userId, tool, args);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
