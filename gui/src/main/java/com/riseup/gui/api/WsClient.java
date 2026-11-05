package com.riseup.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class WsClient {
    private final String server;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private WebSocketClient client;

    public WsClient(String server, String token) {
        this.server = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        this.token = token;
    }

    public void connect(Consumer<JsonNode> onEvent) {
        try {
            String wsBase = server.replaceFirst("^http", "ws");
            String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
            URI uri = URI.create(wsBase + "/ws?token=" + encoded);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                }

                @Override
                public void onMessage(String message) {
                    try {
                        onEvent.accept(mapper.readTree(message));
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                }
            };
            client.connect();
        } catch (Exception ignored) {
        }
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
