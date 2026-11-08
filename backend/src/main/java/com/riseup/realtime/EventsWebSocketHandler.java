package com.riseup.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.api.AuthService;
import com.riseup.error.DomainException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EventsWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketHub hub;
    private final AuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventsWebSocketHandler(WebSocketHub hub, AuthService auth) {
        this.hub = hub;
        this.auth = auth;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = null;
        if (session.getUri() != null) {
            token = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
        }
        try {
            Integer userId = auth.resolveUserId(null, token);
            session.getAttributes().put("userId", userId);
            hub.connect(userId, session);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "hello", "user_id", userId))));
        } catch (DomainException e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "pong"))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Integer id) {
            hub.disconnect(id, session);
        }
    }
}
