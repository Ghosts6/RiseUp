package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Iterator;
import java.util.Map;

public class InsightsPage extends VBox {
    private final ApiClient api;
    private final MainShell shell;
    private final VBox content = new VBox(14);

    public InsightsPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);
        setFillWidth(true);

        getChildren().add(Panels.pageHeader(
                "Your week in mornings",
                "Insights",
                "A friendly read on how you’re waking up — no spreadsheets."));

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("primary-btn");
        Button recompute = new Button("Refresh my profile");
        recompute.getStyleClass().add("secondary-btn");

        refresh.setOnAction(e -> UiKit.runBusy(refresh, "Loading…", () -> {
            load();
            shell.toastOk("Insights updated.");
        }));
        recompute.setOnAction(e -> {
            if (!UiKit.confirm(this, "Refresh profile",
                    "Rebuild your wake profile from recent history?")) {
                return;
            }
            UiKit.runBusy(recompute, "Working…", () -> {
                try {
                    api.post("/api/profile/recompute", java.util.Map.of());
                    shell.toastOk("Profile refreshed.");
                    load();
                } catch (Exception ex) {
                    shell.toastError(ex.getMessage());
                }
            });
        });

        content.setFillWidth(true);
        getChildren().addAll(Panels.actionRow(refresh, recompute), content);
        load();
    }

    private void load() {
        content.getChildren().clear();
        try {
            JsonNode stats = api.get("/api/stats?range=30d");
            JsonNode profile = api.get("/api/profile");

            int days = stats.path("range_days").asInt(30);
            long triggered = stats.path("alarms_triggered").asLong(0);
            long verified = stats.path("alarms_verified").asLong(0);
            long missed = stats.path("alarms_missed").asLong(0);
            long escalations = stats.path("escalations").asLong(0);
            long reminders = stats.path("reminder_events").asLong(0);
            double rate = stats.path("verification_rate").asDouble(0);
            int pct = (int) Math.round(rate * 100);

            String vibe;
            String vibeBlurb;
            if (triggered == 0) {
                vibe = "Getting started";
                vibeBlurb = "Set a few alarms and come back — your story starts after the first ring.";
            } else if (pct >= 80) {
                vibe = "On a roll";
                vibeBlurb = "You’re dismissing alarms like a pro. Keep that streak warm.";
            } else if (pct >= 50) {
                vibe = "Solid mornings";
                vibeBlurb = "More wins than misses — a little consistency will push you over the top.";
            } else {
                vibe = "Room to rise";
                vibeBlurb = "Tough mornings happen. Try an easier challenge or earlier bedtime.";
            }

            content.getChildren().add(Panels.heroCard("◈  " + vibe,
                    UiKit.muted("Last " + days + " days"),
                    new Label(vibeBlurb)));

            FlowPane statsRow = new FlowPane(12, 12);
            statsRow.setMaxWidth(Double.MAX_VALUE);
            statsRow.getChildren().addAll(
                    statTile("☀", "Wake score", pct + "%", "alarms you finished"),
                    statTile("🔔", "Rings", String.valueOf(triggered), "alarms that went off"),
                    statTile("✓", "Verified", String.valueOf(verified), "you proved you’re up"),
                    statTile("☾", "Missed", String.valueOf(missed), "slipped past you"),
                    statTile("↗", "Escalations", String.valueOf(escalations), "backup nudges"),
                    statTile("☑", "Task activity", String.valueOf(reminders), "reminder events")
            );
            content.getChildren().add(Panels.card("At a glance", statsRow));

            VBox story = new VBox(8);
            story.getChildren().add(storyLine("🔔",
                    triggered == 0 ? "No alarms rang yet in this window."
                            : "RiseUp woke you " + triggered + " time" + (triggered == 1 ? "" : "s") + "."));
            story.getChildren().add(storyLine("✓",
                    verified == 0 ? "No verifications yet — the challenge is waiting."
                            : "You cleared " + verified + " of those with a challenge."));
            if (missed > 0) {
                story.getChildren().add(storyLine("☾",
                        missed + " got away — snooze less, or pick a gentler task."));
            }
            if (escalations > 0) {
                story.getChildren().add(storyLine("↗",
                        "Backup kicked in " + escalations + " time" + (escalations == 1 ? "" : "s") + "."));
            }
            content.getChildren().add(Panels.card("Your morning story", story));

            VBox tips = new VBox(10);
            JsonNode optimal = profile.path("optimal_settings");
            if (optimal.isObject() && optimal.size() > 0) {
                tips.getChildren().add(UiKit.muted("Ideas RiseUp learned for you"));
                Iterator<Map.Entry<String, JsonNode>> fields = optimal.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> e = fields.next();
                    tips.getChildren().add(tipRow("✦", humanizeKey(e.getKey()), friendlyValue(e.getValue())));
                }
            } else {
                tips.getChildren().add(UiKit.emptyState(
                        "Profile still warming up",
                        "Use the app for a bit, then hit “Refresh my profile”."));
            }

            JsonNode dataStatus = profile.path("data_status");
            if (!dataStatus.isMissingNode() && !dataStatus.asText("").isBlank()) {
                tips.getChildren().add(tipRow("◔", "Data status", dataStatus.asText()));
            }
            String updated = profile.path("updated_at").asText("");
            if (!updated.isBlank()) {
                tips.getChildren().add(UiKit.muted("Profile updated " + updated.replace('T', ' ')));
            }
            content.getChildren().add(Panels.card("Personal tips", tips));
        } catch (Exception ex) {
            content.getChildren().add(UiKit.emptyState("Couldn’t load insights", ex.getMessage()));
            shell.toastError(ex.getMessage());
        }
    }

    private static VBox statTile(String icon, String title, String value, String hint) {
        Label ic = new Label(icon);
        ic.getStyleClass().add("insight-icon");
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        Label v = new Label(value);
        v.getStyleClass().add("insight-stat-value");
        Label h = new Label(hint);
        h.getStyleClass().add("muted");
        h.setWrapText(true);
        VBox box = new VBox(4, ic, t, v, h);
        box.getStyleClass().add("insight-tile");
        box.setPrefWidth(150);
        box.setMinWidth(130);
        box.setMaxWidth(200);
        return box;
    }

    private static HBox storyLine(String icon, String text) {
        Label ic = new Label(icon);
        ic.getStyleClass().add("insight-icon");
        Label body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add("item-title");
        HBox.setHgrow(body, Priority.ALWAYS);
        HBox row = new HBox(12, ic, body);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static HBox tipRow(String icon, String title, String detail) {
        Label ic = new Label(icon);
        ic.getStyleClass().add("insight-icon");
        Label t = new Label(title);
        t.getStyleClass().add("item-title");
        Label d = new Label(detail);
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        VBox text = new VBox(2, t, d);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, ic, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private static String humanizeKey(String key) {
        String k = key.replace('_', ' ').trim();
        if (k.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    private static String friendlyValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "—";
        }
        if (node.isNumber()) {
            double d = node.asDouble();
            if (d <= 1.0 && d >= 0 && String.valueOf(node).contains(".")) {
                return Math.round(d * 100) + "%";
            }
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "Yes" : "No";
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            node.forEach(n -> {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(friendlyValue(n));
            });
            return sb.isEmpty() ? "None yet" : sb.toString();
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            node.fields().forEachRemaining(e -> {
                if (!sb.isEmpty()) {
                    sb.append(" · ");
                }
                sb.append(humanizeKey(e.getKey())).append(": ").append(friendlyValue(e.getValue()));
            });
            return sb.isEmpty() ? "—" : sb.toString();
        }
        String text = node.asText();
        return text.isBlank() ? "—" : text;
    }
}
