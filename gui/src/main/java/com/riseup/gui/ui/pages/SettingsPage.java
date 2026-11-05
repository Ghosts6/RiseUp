package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsPage extends VBox {
    private static final String[] CHANNELS = {"push", "sms", "call", "email"};

    private final ApiClient api;
    private final MainShell shell;
    private final VBox contactsBox = new VBox(8);
    private final VBox statusBox = new VBox(8);
    private final Set<String> enabledChannels = new LinkedHashSet<>();
    private final List<ToggleButton> channelToggles = new ArrayList<>();

    private final TextField name = UiKit.textField("Your name", "");
    private final TextField phone = UiKit.textField("Phone with country code", "");
    private final TextField quietStart = UiKit.textField("e.g. 22:00", "");
    private final TextField quietEnd = UiKit.textField("e.g. 07:00", "");
    private final CheckBox checkin = new CheckBox("Send a check-in after I dismiss an alarm");
    private final ComboBox<String> checkinDelay = new ComboBox<>();
    private final ComboBox<String> defaultTask = new ComboBox<>();

    public SettingsPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);
        setFillWidth(true);

        getChildren().add(Panels.pageHeader(
                "Preferences",
                "Settings",
                "Plain-language controls — nothing that looks like code."));

        checkinDelay.getItems().addAll("5 minutes", "10 minutes", "15 minutes", "30 minutes");
        checkinDelay.getSelectionModel().select("10 minutes");
        checkinDelay.setMaxWidth(Double.MAX_VALUE);
        defaultTask.getItems().addAll("math", "trivia", "object_detection", "custom");
        defaultTask.getSelectionModel().select("math");
        defaultTask.setMaxWidth(Double.MAX_VALUE);

        FlowPane channelRow = new FlowPane(8, 8);
        for (String ch : CHANNELS) {
            ToggleButton tb = new ToggleButton(prettyChannel(ch));
            tb.getStyleClass().add("filter-chip");
            tb.selectedProperty().addListener((o, was, on) -> {
                if (on) {
                    enabledChannels.add(ch);
                    if (!tb.getStyleClass().contains("active")) {
                        tb.getStyleClass().add("active");
                    }
                } else {
                    enabledChannels.remove(ch);
                    tb.getStyleClass().remove("active");
                }
            });
            channelToggles.add(tb);
            channelRow.getChildren().add(tb);
        }

        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-btn");
        Button reload = new Button("Reload");
        reload.getStyleClass().add("secondary-btn");

        TextField contactName = UiKit.textField("Contact name", "");
        TextField contactPhone = UiKit.textField("Contact phone", "");
        HBox.setHgrow(contactName, Priority.ALWAYS);
        HBox.setHgrow(contactPhone, Priority.ALWAYS);
        Button addContact = new Button("Add");
        addContact.getStyleClass().add("secondary-btn");

        Button testPush = new Button("Test push");
        testPush.getStyleClass().add("ghost-btn");
        Button testSms = new Button("Test SMS");
        testSms.getStyleClass().add("ghost-btn");
        Button testCall = new Button("Test call");
        testCall.getStyleClass().add("ghost-btn");

        Runnable doLoad = () -> UiKit.runBusy(reload, "Loading…", this::loadAll);
        reload.setOnAction(e -> doLoad.run());

        save.setOnAction(e -> {
            if (name.getText().isBlank()) {
                shell.toastError("Name can’t be empty.");
                name.requestFocus();
                return;
            }
            UiKit.runBusy(save, "Saving…", () -> {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", name.getText().trim());
                    body.put("phone", phone.getText().trim());
                    body.put("enabled_channels", new ArrayList<>(enabledChannels));
                    body.put("quiet_hours_start", blankToNull(quietStart.getText()));
                    body.put("quiet_hours_end", blankToNull(quietEnd.getText()));
                    body.put("checkin_enabled", checkin.isSelected());
                    body.put("checkin_delay_minutes", parseMinutes(checkinDelay.getValue(), 10));
                    body.put("default_task_type", defaultTask.getValue());
                    api.patch("/api/prefs", body);
                    shell.toastOk("Settings saved.");
                    loadAll();
                } catch (Exception ex) {
                    shell.toastError(ex.getMessage());
                }
            });
        });
        UiKit.onEnter(name, save::fire);
        UiKit.onEnter(phone, save::fire);

        addContact.setOnAction(e -> {
            if (contactName.getText().isBlank() || contactPhone.getText().isBlank()) {
                shell.toastError("Contact needs both a name and phone.");
                return;
            }
            UiKit.runBusy(addContact, "…", () -> {
                try {
                    api.post("/api/contacts", Map.of(
                            "name", contactName.getText().trim(),
                            "phone", contactPhone.getText().trim()));
                    contactName.clear();
                    contactPhone.clear();
                    shell.toastOk("Contact added.");
                    loadAll();
                } catch (Exception ex) {
                    shell.toastError(ex.getMessage());
                }
            });
        });

        testPush.setOnAction(e -> testChannel("push"));
        testSms.setOnAction(e -> testChannel("sms"));
        testCall.setOnAction(e -> testChannel("call"));

        getChildren().addAll(
                Panels.card("Profile",
                        UiKit.labeled("Name", name),
                        UiKit.labeled("Phone", phone)),
                Panels.card("Notifications",
                        UiKit.muted("Tap to enable the ways RiseUp can reach you"),
                        channelRow,
                        UiKit.labeled("Quiet hours start", quietStart),
                        UiKit.labeled("Quiet hours end", quietEnd),
                        checkin,
                        UiKit.labeled("Check-in delay", checkinDelay),
                        UiKit.labeled("Default wake challenge", defaultTask),
                        Panels.actionRow(save, reload)),
                Panels.card("Emergency contacts",
                        UiKit.muted("People we can ping if an alarm keeps escalating"),
                        contactsBox,
                        new HBox(10, contactName, contactPhone, addContact)),
                Panels.card("Connection status",
                        UiKit.muted("How your connected services look right now"),
                        statusBox),
                Panels.card("Try a test ping",
                        UiKit.muted("Sends a harmless test through each channel"),
                        Panels.actionRow(testPush, testSms, testCall)));
        doLoad.run();
        javafx.application.Platform.runLater(name::requestFocus);
    }

    private void loadAll() {
        try {
            JsonNode p = api.get("/api/prefs");
            name.setText(p.path("user").path("name").asText(""));
            phone.setText(p.path("user").path("phone").asText(""));

            JsonNode prefs = p.path("preferences");
            quietStart.setText(prefs.path("quiet_hours_start").asText(""));
            quietEnd.setText(prefs.path("quiet_hours_end").asText(""));
            checkin.setSelected(prefs.path("checkin_enabled").asBoolean(false));
            int delay = prefs.path("checkin_delay_minutes").asInt(10);
            checkinDelay.getSelectionModel().select(delay + " minutes");
            String task = prefs.path("default_task_type").asText("math");
            if (defaultTask.getItems().contains(task)) {
                defaultTask.getSelectionModel().select(task);
            }

            enabledChannels.clear();
            if (prefs.path("enabled_channels").isArray()) {
                prefs.path("enabled_channels").forEach(n -> enabledChannels.add(n.asText()));
            }
            for (int i = 0; i < CHANNELS.length; i++) {
                ToggleButton tb = channelToggles.get(i);
                boolean on = enabledChannels.contains(CHANNELS[i]);
                tb.setSelected(on);
                tb.getStyleClass().remove("active");
                if (on) {
                    tb.getStyleClass().add("active");
                }
            }

            contactsBox.getChildren().clear();
            JsonNode contacts = p.path("contacts");
            if (!contacts.isArray() || contacts.isEmpty()) {
                contactsBox.getChildren().add(UiKit.emptyState(
                        "No contacts yet",
                        "Add someone you trust for escalation backups."));
            } else {
                for (JsonNode c : contacts) {
                    contactsBox.getChildren().add(contactRow(c));
                }
            }

            statusBox.getChildren().clear();
            statusBox.getChildren().add(statusRow("♫", "Notifications",
                    summarizeMap(p.path("integrations"))));
            statusBox.getChildren().add(statusRow("✦", "AI helpers",
                    summarizeMap(p.path("llm"))));
            String tz = p.path("user").path("timezone").asText("");
            if (!tz.isBlank()) {
                statusBox.getChildren().add(statusRow("◷", "Timezone", tz));
            }
        } catch (Exception ex) {
            shell.toastError(ex.getMessage());
        }
    }

    private HBox contactRow(JsonNode c) {
        int id = c.path("id").asInt();
        String nm = c.path("name").asText("Contact");
        String ph = c.path("phone").asText("");
        String consent = c.path("consent_status").asText("pending");

        Label title = new Label(nm);
        title.getStyleClass().add("item-title");
        Label meta = new Label(ph + " · " + prettyConsent(consent));
        meta.getStyleClass().add("muted");
        VBox text = new VBox(2, title, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button remove = new Button("Remove");
        remove.getStyleClass().add("ghost-btn");
        remove.setOnAction(e -> {
            if (!UiKit.confirm(this, "Remove contact", "Remove “" + nm + "”?")) {
                return;
            }
            try {
                api.delete("/api/contacts/" + id);
                shell.toastOk("Contact removed.");
                loadAll();
            } catch (Exception ex) {
                shell.toastError(ex.getMessage());
            }
        });

        HBox row = new HBox(12, UiKit.statusBadge(consent), text, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private static HBox statusRow(String icon, String title, String detail) {
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

    private static String summarizeMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "Not configured yet";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            List<String> parts = new ArrayList<>();
            node.fields().forEachRemaining(e -> {
                String val = e.getValue().asText(e.getValue().toString());
                String status = val.toLowerCase(Locale.ROOT);
                String mark = status.contains("ok") || status.contains("ready") || status.contains("true")
                        ? "ready" : (status.contains("mock") ? "demo mode" : val);
                parts.add(prettyChannel(e.getKey()) + ": " + mark);
            });
            return parts.isEmpty() ? "All quiet" : String.join(" · ", parts);
        }
        return node.asText(node.toString());
    }

    private void testChannel(String channel) {
        try {
            JsonNode result = api.post("/api/prefs/test", Map.of("channel", channel));
            boolean ok = result.path("success").asBoolean(false);
            String detail = result.path("detail").asText(result.toString());
            if (ok) {
                shell.toastOk(prettyChannel(channel) + " test sent.");
            } else {
                shell.toastError(prettyChannel(channel) + ": " + detail);
            }
        } catch (Exception ex) {
            shell.toastError(ex.getMessage());
        }
    }

    private static String prettyChannel(String ch) {
        if (ch == null || ch.isBlank()) {
            return "";
        }
        return ch.substring(0, 1).toUpperCase(Locale.ROOT) + ch.substring(1);
    }

    private static String prettyConsent(String c) {
        return switch (c == null ? "" : c.toLowerCase(Locale.ROOT)) {
            case "granted", "yes", "accepted" -> "Consented";
            case "denied", "no" -> "Declined";
            default -> "Pending consent";
        };
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static int parseMinutes(String label, int fallback) {
        if (label == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return fallback;
        }
    }
}
