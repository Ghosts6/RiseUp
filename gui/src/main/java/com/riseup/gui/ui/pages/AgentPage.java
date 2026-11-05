package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.UiKit;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Modern GPT/Gemini-style RiseUp agent chat. */
public class AgentPage extends BorderPane {
    private final ApiClient api;
    private final MainShell shell;
    private final VBox transcript = new VBox(18);
    private final ScrollPane scroll = new ScrollPane();
    private final VBox emptyState = new VBox(14);
    private final HBox suggestions = new HBox(8);
    private final TextArea input = new TextArea();
    private final Label status = new Label("GPT-6 Luna · RiseUp knowledge");
    private final Button sendBtn = new Button("↑");
    private final List<Map<String, String>> history = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-chat");
        t.setDaemon(true);
        return t;
    });
    private boolean busy;
    private HBox typingRow;

    public AgentPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().addAll("page", "agent-page");
        setPadding(new Insets(8, 10, 12, 10));

        setTop(buildHeader());
        setCenter(buildChatColumn());
        setBottom(buildComposer());

        showEmptyState();

        sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                io.shutdownNow();
            }
        });
        Platform.runLater(input::requestFocus);
    }

    private HBox buildHeader() {
        Label mark = new Label("✦");
        mark.getStyleClass().add("agent-avatar");

        Label title = new Label("RiseUp Agent");
        title.getStyleClass().add("agent-header-title");
        Label sub = new Label("Ask about the app, alarms, routines, or get feedback");
        sub.getStyleClass().add("agent-header-sub");
        VBox titles = new VBox(2, title, sub);

        Button clear = new Button("New chat");
        clear.getStyleClass().addAll("ghost-btn", "agent-new-chat");
        clear.setOnAction(e -> resetChat());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, mark, titles, spacer, clear);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("agent-header");
        header.setPadding(new Insets(4, 8, 12, 8));
        return header;
    }

    private StackPane buildChatColumn() {
        transcript.setFillWidth(true);
        transcript.setPadding(new Insets(8, 18, 24, 18));
        transcript.setMaxWidth(760);
        transcript.setAlignment(Pos.TOP_CENTER);

        emptyState.getStyleClass().add("agent-empty");
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setMaxWidth(640);
        Label heroIcon = new Label("✦");
        heroIcon.getStyleClass().add("agent-empty-icon");
        Label heroTitle = new Label("How can I help you wake up better?");
        heroTitle.getStyleClass().add("agent-empty-title");
        heroTitle.setWrapText(true);
        Label heroSub = new Label("I know RiseUp — challenges, escalation, tasks, sounds, and more.");
        heroSub.getStyleClass().add("agent-empty-sub");
        heroSub.setWrapText(true);
        emptyState.getChildren().addAll(heroIcon, heroTitle, heroSub);

        StackPane feed = new StackPane(emptyState, transcript);
        feed.getStyleClass().add("agent-feed");
        StackPane.setAlignment(emptyState, Pos.CENTER);
        StackPane.setAlignment(transcript, Pos.TOP_CENTER);

        scroll.setContent(feed);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("agent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox column = new VBox(scroll);
        column.setAlignment(Pos.TOP_CENTER);
        column.getStyleClass().add("agent-column");
        StackPane wrap = new StackPane(column);
        wrap.getStyleClass().add("agent-column-wrap");
        return wrap;
    }

    private VBox buildComposer() {
        suggestions.setAlignment(Pos.CENTER_LEFT);
        suggestions.getStyleClass().add("agent-suggestions");
        suggestions.getChildren().addAll(
                suggest("How do challenges work?"),
                suggest("What if I ignore an alarm?"),
                suggest("Plan a better wake routine"),
                suggest("How do Tasks & Events work?"));

        input.setPromptText("Message RiseUp Agent…");
        input.setPrefRowCount(1);
        input.setWrapText(true);
        input.getStyleClass().addAll("field", "agent-input");
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                send();
            }
        });
        HBox.setHgrow(input, Priority.ALWAYS);

        sendBtn.getStyleClass().addAll("primary-btn", "agent-send");
        sendBtn.setOnAction(e -> send());

        HBox bar = new HBox(10, input, sendBtn);
        bar.setAlignment(Pos.BOTTOM_CENTER);
        bar.getStyleClass().add("agent-composer-bar");

        status.getStyleClass().add("agent-status");

        VBox composer = new VBox(10, suggestions, bar, status);
        composer.setAlignment(Pos.CENTER);
        composer.setMaxWidth(760);
        composer.setPadding(new Insets(8, 18, 4, 18));
        composer.getStyleClass().add("agent-composer");

        HBox dock = new HBox(composer);
        dock.setAlignment(Pos.CENTER);
        dock.getStyleClass().add("agent-composer-dock");
        return new VBox(dock);
    }

    private Button suggest(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("agent-suggest");
        b.setOnAction(e -> {
            input.setText(text);
            send();
        });
        return b;
    }

    private void resetChat() {
        history.clear();
        transcript.getChildren().clear();
        typingRow = null;
        showEmptyState();
        status.setText("GPT-6 Luna · RiseUp knowledge");
        input.requestFocus();
    }

    private void showEmptyState() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        suggestions.setVisible(true);
        suggestions.setManaged(true);
    }

    private void hideEmptyState() {
        emptyState.setVisible(false);
        emptyState.setManaged(false);
    }

    private void send() {
        if (busy) {
            return;
        }
        String message = input.getText() == null ? "" : input.getText().trim();
        if (message.isBlank()) {
            shell.toastError("Type a message first.");
            return;
        }
        input.clear();
        hideEmptyState();
        addBubble("user", message);
        history.add(Map.of("role", "user", "content", message));
        busy = true;
        sendBtn.setDisable(true);
        status.setText("Thinking…");
        showTyping();

        List<Map<String, String>> snapshot = new ArrayList<>(history);
        if (!snapshot.isEmpty()) {
            snapshot.remove(snapshot.size() - 1);
        }

        io.execute(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("message", message);
                body.put("history", snapshot);
                JsonNode resp = api.post("/api/agent/chat", body);
                String reply = resp.path("reply").asText("Sorry — empty reply.");
                String model = resp.path("model").asText("");
                Platform.runLater(() -> {
                    hideTyping();
                    history.add(Map.of("role", "assistant", "content", reply));
                    addBubble("assistant", reply);
                    status.setText(model.isBlank() ? "Ready" : model);
                    busy = false;
                    sendBtn.setDisable(false);
                    if (history.size() >= 2) {
                        suggestions.setVisible(false);
                        suggestions.setManaged(false);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    hideTyping();
                    addBubble("assistant", "I couldn’t reach the agent: " + UiKit.userError(ex));
                    status.setText("Unavailable — rebuild backend if you see 404");
                    busy = false;
                    sendBtn.setDisable(false);
                    shell.toastError(UiKit.userError(ex));
                });
            }
        });
    }

    private void showTyping() {
        hideTyping();
        Label dots = new Label("●  ●  ●");
        dots.getStyleClass().add("agent-typing-dots");
        VBox bubble = new VBox(dots);
        bubble.getStyleClass().addAll("agent-bubble", "agent-bubble-agent", "agent-typing");
        HBox row = messageRow(avatar(), bubble, false);
        typingRow = row;
        transcript.getChildren().add(row);
        scrollToBottom();
    }

    private void hideTyping() {
        if (typingRow != null) {
            transcript.getChildren().remove(typingRow);
            typingRow = null;
        }
    }

    private void addBubble(String role, String text) {
        Label body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add("agent-bubble-text");

        VBox bubble = new VBox(body);
        bubble.getStyleClass().addAll(
                "agent-bubble",
                "user".equals(role) ? "agent-bubble-user" : "agent-bubble-agent");
        bubble.setMaxWidth("user".equals(role) ? 520 : 580);

        HBox row = "user".equals(role)
                ? messageRow(null, bubble, true)
                : messageRow(avatar(), bubble, false);
        transcript.getChildren().add(row);
        scrollToBottom();
    }

    private static Label avatar() {
        Label a = new Label("✦");
        a.getStyleClass().add("agent-msg-avatar");
        return a;
    }

    private static HBox messageRow(Label avatar, VBox bubble, boolean user) {
        HBox row = new HBox(10);
        row.setMaxWidth(Double.MAX_VALUE);
        if (user) {
            row.setAlignment(Pos.TOP_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            if (avatar != null) {
                row.getChildren().add(avatar);
            }
            row.getChildren().add(bubble);
            HBox.setHgrow(bubble, Priority.ALWAYS);
        }
        return row;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scroll.applyCss();
            scroll.layout();
            scroll.setVvalue(1.0);
        });
    }
}
