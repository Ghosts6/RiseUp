package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.agent.AgentChatStore;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Icons;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Modern GPT/Gemini-style RiseUp agent chat with switchable history. */
public class AgentPage extends BorderPane {
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());

    private final ApiClient api;
    private final MainShell shell;
    private final AgentChatStore store = AgentChatStore.get();

    private final VBox historyList = new VBox(4);
    private final VBox historyRail = new VBox(12);
    private final VBox transcript = new VBox(18);
    private final ScrollPane scroll = new ScrollPane();
    private final VBox emptyState = new VBox(14);
    private final HBox suggestions = new HBox(8);
    private final TextArea input = new TextArea();
    private final Label status = new Label("GPT-6 Luna · RiseUp knowledge");
    private final Button sendBtn = new Button();
    private final Button historyToggle = new Button("History");
    private final List<Map<String, String>> history = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-chat");
        t.setDaemon(true);
        return t;
    });
    private boolean busy;
    private boolean historyOpen = true;
    private HBox typingRow;

    public AgentPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().addAll("page", "agent-page");
        setPadding(new Insets(4, 6, 8, 6));

        setCenter(buildMain());
        setRight(buildHistoryRail());

        loadActiveConversation();
        refreshHistoryRail();
        applyHistoryVisibility();

        sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                io.shutdownNow();
            }
        });
        Platform.runLater(input::requestFocus);
    }

    private VBox buildHistoryRail() {
        Label heading = new Label("Chats");
        heading.getStyleClass().add("agent-history-heading");
        HBox.setHgrow(heading, Priority.ALWAYS);

        Button close = new Button();
        close.getStyleClass().addAll("ghost-btn", "icon-btn", "agent-history-close");
        Icons.setGraphic(close, Icons.CIRCLE_X, 14, Icons.MUTED_LIGHT);
        close.setOnAction(e -> setHistoryOpen(false));

        HBox top = new HBox(8, heading, close);
        top.setAlignment(Pos.CENTER_LEFT);

        Button neu = new Button("New chat");
        neu.getStyleClass().addAll("ghost-btn", "agent-history-new");
        Icons.setGraphic(neu, Icons.MESSAGE_PLUS, 14, Icons.MUTED_LIGHT);
        neu.setMaxWidth(Double.MAX_VALUE);
        neu.setOnAction(e -> startNewChat());

        historyList.getStyleClass().add("agent-history-list");
        historyList.setFillWidth(true);

        ScrollPane railScroll = new ScrollPane(historyList);
        railScroll.setFitToWidth(true);
        railScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        railScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        railScroll.getStyleClass().add("agent-history-scroll");
        VBox.setVgrow(railScroll, Priority.ALWAYS);

        historyRail.getChildren().setAll(top, neu, railScroll);
        historyRail.getStyleClass().add("agent-history-rail");
        historyRail.setPrefWidth(228);
        historyRail.setMinWidth(200);
        historyRail.setMaxWidth(248);
        historyRail.setPadding(new Insets(8, 6, 8, 10));
        return historyRail;
    }

    private BorderPane buildMain() {
        BorderPane main = new BorderPane();
        main.getStyleClass().add("agent-main");
        main.setTop(buildHeader());
        main.setCenter(buildChatColumn());
        main.setBottom(buildComposer());
        return main;
    }

    private HBox buildHeader() {
        Label mark = new Label();
        mark.getStyleClass().add("agent-avatar");
        mark.setGraphic(Icons.view(Icons.SPARKLES, 16, Icons.WHITE));
        mark.setAlignment(Pos.CENTER);

        Label title = new Label("RiseUp Agent");
        title.getStyleClass().add("agent-header-title");
        Label sub = new Label("Ask about the app, alarms, routines, or get feedback");
        sub.getStyleClass().add("agent-header-sub");
        VBox titles = new VBox(2, title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        historyToggle.getStyleClass().addAll("ghost-btn", "agent-history-toggle");
        Icons.setGraphic(historyToggle, Icons.HISTORY, 15, Icons.MUTED_LIGHT);
        historyToggle.setOnAction(e -> setHistoryOpen(!historyOpen));

        HBox header = new HBox(12, mark, titles, spacer, historyToggle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("agent-header");
        header.setPadding(new Insets(4, 8, 12, 8));
        return header;
    }

    private void setHistoryOpen(boolean open) {
        historyOpen = open;
        applyHistoryVisibility();
    }

    private void applyHistoryVisibility() {
        historyRail.setVisible(historyOpen);
        historyRail.setManaged(historyOpen);
        historyToggle.getStyleClass().remove("active");
        if (historyOpen) {
            historyToggle.getStyleClass().add("active");
            historyToggle.setText("Hide");
        } else {
            historyToggle.setText("History");
        }
        boolean dark = getScene() != null && getScene().getRoot().getStyleClass().contains("dark");
        Icons.setGraphic(historyToggle, Icons.HISTORY, 15, Icons.mutedColor(dark));
    }

    private StackPane buildChatColumn() {
        transcript.setFillWidth(true);
        transcript.setPadding(new Insets(8, 18, 24, 18));
        transcript.setMaxWidth(760);
        transcript.setAlignment(Pos.TOP_CENTER);

        emptyState.getStyleClass().add("agent-empty");
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setMaxWidth(640);
        Label heroIcon = new Label();
        heroIcon.getStyleClass().add("agent-empty-icon");
        heroIcon.setGraphic(Icons.view(Icons.SPARKLES, 36, Icons.LIGHT_ACCENT));
        Label heroTitle = new Label("How can I help you wake up better?");
        heroTitle.getStyleClass().add("agent-empty-title");
        heroTitle.setWrapText(true);
        Label heroSub = new Label("I know RiseUp: challenges, escalation, tasks, sounds, and more.");
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
                suggest("What's on today?"),
                suggest("Remind me to drink water tomorrow at 9am"),
                suggest("How do challenges work?"),
                suggest("What if I ignore an alarm?"));

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

        sendBtn.getStyleClass().addAll("primary-btn", "agent-send", "icon-btn");
        sendBtn.setGraphic(Icons.view(Icons.SEND, 16, Icons.WHITE));
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

    private void refreshHistoryRail() {
        historyList.getChildren().clear();
        String activeId = store.active().id;
        for (AgentChatStore.Conversation c : store.list()) {
            historyList.getChildren().add(historyRow(c, c.id.equals(activeId)));
        }
        if (historyList.getChildren().isEmpty()) {
            Label empty = new Label("No chats yet");
            empty.getStyleClass().add("agent-history-empty");
            historyList.getChildren().add(empty);
        }
    }

    private HBox historyRow(AgentChatStore.Conversation c, boolean active) {
        Label title = new Label(c.title == null || c.title.isBlank() ? "New chat" : c.title);
        title.getStyleClass().add("agent-history-title");
        title.setWrapText(true);
        title.setMaxWidth(148);

        Label meta = new Label(relativeTime(c.updatedAt));
        meta.getStyleClass().add("agent-history-meta");

        VBox text = new VBox(2, title, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button del = new Button();
        del.getStyleClass().addAll("ghost-btn", "icon-btn", "agent-history-delete");
        Icons.setGraphic(del, Icons.TRASH, 13, Icons.MUTED_LIGHT);
        del.setOnAction(e -> {
            e.consume();
            store.delete(c.id);
            loadActiveConversation();
            refreshHistoryRail();
        });

        HBox row = new HBox(8, text, del);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("agent-history-item");
        if (active) {
            row.getStyleClass().add("active");
        }
        row.setPadding(new Insets(10, 10, 10, 12));
        row.setOnMouseClicked(e -> {
            if (busy || e.getTarget() == del || e.getTarget() == del.getGraphic()) {
                return;
            }
            if (!c.id.equals(store.active().id)) {
                persistCurrent();
                store.setActive(c.id);
                loadActiveConversation();
                refreshHistoryRail();
            }
        });
        return row;
    }

    private static String relativeTime(Instant when) {
        if (when == null) {
            return "";
        }
        Duration d = Duration.between(when, Instant.now());
        if (d.toMinutes() < 1) {
            return "Just now";
        }
        if (d.toHours() < 1) {
            return d.toMinutes() + "m ago";
        }
        if (d.toHours() < 24) {
            return d.toHours() + "h ago";
        }
        if (d.toDays() < 7) {
            return d.toDays() + "d ago";
        }
        return DAY_FMT.format(when);
    }

    private void startNewChat() {
        if (busy) {
            return;
        }
        persistCurrent();
        store.startNew();
        loadActiveConversation();
        refreshHistoryRail();
        input.requestFocus();
    }

    private void persistCurrent() {
        List<AgentChatStore.Message> msgs = new ArrayList<>();
        for (Map<String, String> m : history) {
            msgs.add(new AgentChatStore.Message(m.get("role"), m.get("content")));
        }
        store.touchActive(msgs);
    }

    private void loadActiveConversation() {
        history.clear();
        transcript.getChildren().clear();
        typingRow = null;
        busy = false;
        sendBtn.setDisable(false);

        AgentChatStore.Conversation c = store.active();
        for (AgentChatStore.Message m : c.messages) {
            history.add(Map.of("role", m.role(), "content", m.content()));
            addBubble(m.role(), m.content());
        }

        if (history.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
            suggestions.setVisible(false);
            suggestions.setManaged(false);
        }
        status.setText("GPT-6 Luna · RiseUp knowledge");
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
        persistCurrent();
        refreshHistoryRail();

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
                String reply = resp.path("reply").asText("Sorry, empty reply.");
                String model = resp.path("model").asText("");
                JsonNode proposed = resp.path("proposed_action");
                JsonNode briefing = resp.path("briefing");
                Platform.runLater(() -> {
                    hideTyping();
                    history.add(Map.of("role", "assistant", "content", reply));
                    addBubble("assistant", reply);
                    if (briefing != null && briefing.isObject() && !briefing.isEmpty()) {
                        addBriefingCard(briefing);
                    }
                    if (proposed != null && proposed.isObject() && proposed.has("tool")) {
                        addProposedAction(proposed);
                    }
                    persistCurrent();
                    refreshHistoryRail();
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
                    status.setText("Unavailable. Rebuild the backend if you see 404.");
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

    private void addBriefingCard(JsonNode briefing) {
        Label title = new Label("Morning briefing");
        title.getStyleClass().add("agent-action-title");
        Label summary = new Label(briefing.path("summary").asText(""));
        summary.setWrapText(true);
        summary.getStyleClass().add("agent-bubble-text");
        int alarms = briefing.path("alarms").size();
        int tasks = briefing.path("reminders").size();
        Label meta = new Label(alarms + " alarms · " + tasks + " tasks · " + briefing.path("date_local").asText(""));
        meta.getStyleClass().add("agent-history-meta");
        VBox card = new VBox(8, title, summary, meta);
        card.getStyleClass().addAll("agent-bubble", "agent-bubble-agent", "agent-action-card");
        card.setMaxWidth(580);
        transcript.getChildren().add(messageRow(avatar(), card, false));
        scrollToBottom();
    }

    private void addProposedAction(JsonNode proposed) {
        String tool = proposed.path("tool").asText("");
        String confirm = proposed.path("confirm_message").asText("Confirm this action?");
        Label title = new Label(confirm);
        title.setWrapText(true);
        title.getStyleClass().add("agent-action-title");

        Button yes = new Button("Confirm");
        yes.getStyleClass().add("primary-btn");
        Button no = new Button("Cancel");
        no.getStyleClass().add("ghost-btn");
        HBox actions = new HBox(8, yes, no);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, title, actions);
        card.getStyleClass().addAll("agent-bubble", "agent-bubble-agent", "agent-action-card");
        card.setMaxWidth(580);
        HBox row = messageRow(avatar(), card, false);
        transcript.getChildren().add(row);
        scrollToBottom();

        no.setOnAction(e -> {
            actions.setDisable(true);
            addBubble("assistant", "Okay — cancelled.");
            history.add(Map.of("role", "assistant", "content", "Okay — cancelled."));
            persistCurrent();
        });
        yes.setOnAction(e -> {
            actions.setDisable(true);
            Map<String, Object> args = new LinkedHashMap<>();
            JsonNode argsNode = proposed.path("args");
            if (argsNode.isObject()) {
                argsNode.fields().forEachRemaining(f -> {
                    JsonNode v = f.getValue();
                    if (v.isNumber()) {
                        args.put(f.getKey(), v.numberValue());
                    } else if (v.isBoolean()) {
                        args.put(f.getKey(), v.asBoolean());
                    } else if (!v.isNull()) {
                        args.put(f.getKey(), v.asText());
                    }
                });
            }
            io.execute(() -> {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("tool", tool);
                    body.put("args", args);
                    JsonNode resp = api.post("/api/agent/tools/execute", body);
                    String msg = resp.path("message").asText("Done.");
                    Platform.runLater(() -> {
                        addBubble("assistant", msg);
                        history.add(Map.of("role", "assistant", "content", msg));
                        persistCurrent();
                        refreshHistoryRail();
                        shell.toastOk(msg);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        addBubble("assistant", "Couldn’t complete that: " + UiKit.userError(ex));
                        shell.toastError(UiKit.userError(ex));
                    });
                }
            });
        });
    }

    private static Label avatar() {
        Label a = new Label();
        a.getStyleClass().add("agent-msg-avatar");
        a.setGraphic(Icons.view(Icons.SPARKLES, 13, Icons.WHITE));
        a.setAlignment(Pos.CENTER);
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
