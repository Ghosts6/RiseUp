package com.riseup.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WsClient {
    private final String server;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ScheduledExecutorService reconnectPool =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "riseup-ws-reconnect");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private WebSocketClient client;
    private Consumer<JsonNode> onEvent;
    private Runnable onOpen;
    private Runnable onClose;
    private int attempt;

    public WsClient(String server, String token) {
        this.server = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        this.token = token;
    }

    public void connect(Consumer<JsonNode> onEvent) {
        connect(onEvent, null, null);
    }

    public void connect(Consumer<JsonNode> onEvent, Runnable onOpen, Runnable onClose) {
        this.onEvent = onEvent;
        this.onOpen = onOpen;
        this.onClose = onClose;
        closed.set(false);
        attempt = 0;
        openSocket();
    }

    private void openSocket() {
        if (closed.get()) {
            return;
        }
        try {
            String wsBase = server.replaceFirst("^http", "ws");
            String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
            URI uri = URI.create(wsBase + "/ws?token=" + encoded);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    attempt = 0;
                    if (onOpen != null) {
                        Platform.runLater(onOpen);
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode node = mapper.readTree(message);
                        if (onEvent != null) {
                            onEvent.accept(node);
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (onClose != null) {
                        Platform.runLater(onClose);
                    }
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    if (onClose != null) {
                        Platform.runLater(onClose);
                    }
                }
            };
            client.connect();
        } catch (Exception ignored) {
            if (onClose != null) {
                Platform.runLater(onClose);
            }
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (closed.get()) {
            return;
        }
        attempt++;
        long delaySec = Math.min(30, 1L << Math.min(attempt, 4));
        reconnectPool.schedule(this::openSocket, delaySec, TimeUnit.SECONDS);
    }

    public void close() {
        closed.set(true);
        reconnectPool.shutdownNow();
        if (client != null) {
            client.close();
        }
    }
}
