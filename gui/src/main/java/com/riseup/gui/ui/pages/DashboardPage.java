package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.api.ApiException;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class DashboardPage extends VBox {
    private static final ObservableList<String> LIVE = FXCollections.observableArrayList();

    private final ApiClient api;
    private final MainShell shell;
    private final VBox alarmsBox = new VBox(10);
    private final VBox notifBox = new VBox(8);
    private final Label health = new Label("Loading system status…");
    private final Label alarmsHint = UiKit.muted("");
    private final Label notifHint = UiKit.muted("");

    public DashboardPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Overview",
                "Dashboard",
                "Your alarms and notifications — when one rings, open it and solve the challenge."));

        Button askAgent = new Button("Talk with AI Agent");
        askAgent.getStyleClass().add("primary-btn");
        askAgent.setOnAction(e -> shell.navigate("Agent"));
        Label agentBlurb = UiKit.muted(
                "RiseUp is built around an AI agent — escalations, reminder parsing, and recommendations. "
                        + "Ask it anything about the app or your wake routine.");
        agentBlurb.setWrapText(true);
        VBox agentCard = Panels.heroCard("AI Agent", agentBlurb, Panels.actionRow(askAgent));

        health.getStyleClass().add("muted");
        health.setWrapText(true);

        ListView<String> live = new ListView<>(LIVE);
        live.setPrefHeight(140);
        live.setPlaceholder(UiKit.muted("Live events will appear here when the API pushes updates."));

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("primary-btn");
        Button add = new Button("New alarm");
        add.getStyleClass().add("secondary-btn");
        add.setOnAction(e -> shell.navigate("New Alarm"));
        Button markRead = new Button("Mark all read");
        markRead.getStyleClass().add("ghost-btn");
        markRead.setOnAction(e -> UiKit.runBusy(markRead, "…", () -> {
            try {
                api.post("/api/notifications/read", java.util.Map.of());
                shell.toastOk("Notifications marked read.");
                load();
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }));
        refresh.setOnAction(e -> load());

        VBox healthCard = Panels.heroCard("System status", health);
        VBox alarmsCard = Panels.card("Alarms", "Edit or enable — ringing alarms need a challenge answer",
                alarmsHint, alarmsBox);
        VBox notifCard = Panels.card("Notifications", notifHint, notifBox);
        VBox liveCard = Panels.card("Live events", live);
        HBox.setHgrow(alarmsCard, Priority.ALWAYS);
        HBox.setHgrow(notifCard, Priority.ALWAYS);

        getChildren().addAll(
                agentCard,
                healthCard,
                Panels.actionRow(refresh, add, markRead),
                alarmsCard,
                new HBox(14, notifCard, liveCard));
        load();
    }

    public static void pushLive(String line) {
        LIVE.add(0, line);
        if (LIVE.size() > 40) {
            LIVE.remove(40, LIVE.size());
        }
    }

    private void load() {
        try {
            JsonNode h = api.get("/api/health");
            health.setText("Status " + h.path("status").asText()
                    + " · " + h.path("environment").asText()
                    + " · v" + h.path("version").asText()
                    + " · " + api.base());

            alarmsBox.getChildren().clear();
            int alarmCount = 0;
            for (JsonNode a : api.get("/api/alarms")) {
                alarmsBox.getChildren().add(alarmRow(a));
                alarmCount++;
            }
            if (alarmCount == 0) {
                alarmsHint.setText("");
                alarmsBox.getChildren().add(UiKit.emptyState(
                        "No alarms yet",
                        "Create one with “New alarm” — takes under a minute."));
            } else {
                alarmsHint.setText(alarmCount + (alarmCount == 1 ? " alarm" : " alarms"));
            }

            notifBox.getChildren().clear();
            int unread = 0;
            int total = 0;
            for (JsonNode n : api.get("/api/notifications")) {
                total++;
                boolean read = n.path("read").asBoolean(false);
                if (!read) {
                    unread++;
                }
                Label row = new Label((read ? "" : "● ") + n.path("kind").asText() + ": " + n.path("title").asText());
                row.getStyleClass().add(read ? "muted" : "item-title");
                row.setWrapText(true);
                notifBox.getChildren().add(row);
            }
            if (total == 0) {
                notifHint.setText("");
                notifBox.getChildren().add(UiKit.emptyState("Inbox clear", "No notifications right now."));
            } else {
                notifHint.setText(unread + " unread · " + total + " total");
            }
        } catch (ApiException ex) {
            health.setText(UiKit.userError(ex));
            shell.toastError(UiKit.userError(ex));
        }
    }

    private HBox alarmRow(JsonNode a) {
        int id = a.path("id").asInt();
        boolean active = a.path("is_active").asBoolean(true);
        String label = a.path("label").asText("Alarm");
        String time = a.path("time").asText("--:--");
        String status = a.path("status").asText("scheduled");
        String repeat = a.path("repeat_pattern").asText("once");
        boolean ringing = isRinging(status);
        boolean awaitingCheckin = "awaiting_checkin".equalsIgnoreCase(status);

        Label title = new Label(friendlyTime(time) + "  ·  " + label);
        title.getStyleClass().add("item-title");
        String metaText = (active ? "On" : "Off")
                + " · " + prettyRepeat(repeat)
                + " · " + a.path("verification_task_type").asText("math");
        if (ringing) {
            metaText += " · answer the challenge";
        } else if (awaitingCheckin) {
            metaText += " · confirm you’re up";
        }
        Label meta = new Label(metaText);
        meta.getStyleClass().add("muted");
        VBox text = new VBox(3, title, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (ringing) {
            Button solve = new Button("Solve challenge");
            solve.getStyleClass().add("primary-btn");
            solve.setOnAction(e -> shell.openRinging(id));
            actions.getChildren().add(solve);
        } else if (awaitingCheckin) {
            Button confirm = new Button("I'm up");
            confirm.getStyleClass().add("primary-btn");
            confirm.setOnAction(e -> shell.openCheckIn(id));
            actions.getChildren().add(confirm);
        } else {
            Button edit = new Button("Edit");
            edit.getStyleClass().add("secondary-btn");
            edit.setOnAction(e -> shell.openAlarmEditor(id));

            Button toggle = new Button(active ? "Turn off" : "Turn on");
            toggle.getStyleClass().add("ghost-btn");
            toggle.setOnAction(e -> UiKit.runBusy(toggle, "…", () -> {
                try {
                    api.post("/api/alarms/" + id + "/toggle", java.util.Map.of("active", !active));
                    shell.toastOk(active ? "Alarm turned off." : "Alarm turned on.");
                    load();
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                    if (UiKit.userError(ex).toLowerCase().contains("ringing")
                            || UiKit.userError(ex).toLowerCase().contains("challenge")) {
                        shell.openRinging(id);
                    }
                }
            }));

            Button delete = new Button("Delete");
            delete.getStyleClass().add("ghost-btn");
            delete.setOnAction(e -> {
                if (!UiKit.confirm(this, "Delete alarm", "Delete “" + label + "” at " + time + "?")) {
                    return;
                }
                try {
                    api.delete("/api/alarms/" + id);
                    shell.toastOk("Alarm deleted.");
                    load();
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            });
            actions.getChildren().addAll(edit, toggle, delete);
        }

        HBox row = new HBox(12, UiKit.statusBadge(status), text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private static boolean isRinging(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toLowerCase();
        return s.contains("ring") || s.contains("escalat") || "snoozed".equals(s);
    }

    private static String friendlyTime(String hhmm) {
        try {
            var t = java.time.LocalTime.parse(hhmm);
            int h = t.getHour() % 12;
            if (h == 0) {
                h = 12;
            }
            return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " AM" : " PM");
        } catch (Exception e) {
            return hhmm;
        }
    }

    private static String prettyRepeat(String pattern) {
        if (pattern == null || "once".equalsIgnoreCase(pattern)) {
            return "Once";
        }
        if (pattern.toLowerCase().startsWith("custom:")) {
            return "Custom";
        }
        return pattern.substring(0, 1).toUpperCase() + pattern.substring(1);
    }
}
