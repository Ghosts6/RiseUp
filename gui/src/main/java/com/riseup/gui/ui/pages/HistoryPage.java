package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class HistoryPage extends VBox {
    private final ApiClient api;
    private final MainShell shell;
    private final VBox listBox = new VBox(0);
    private final VBox detailBox = new VBox(8);
    private final Label detailTitle = new Label("Select an item");
    private final Label hint = UiKit.muted("");
    private String typeFilter = "all";
    private int sinceDays = 30;

    public HistoryPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(18);

        getChildren().add(Panels.pageHeader(
                "Archive",
                "History",
                "Browse past activity. Open an escalation to see Why? reasoning."));

        detailTitle.getStyleClass().add("card-title");
        detailBox.getChildren().add(UiKit.muted("Select an item to see details…"));

        Button fAll = UiKit.filterChip("All");
        Button fAlarm = UiKit.filterChip("Alarms");
        Button fReminder = UiKit.filterChip("Reminders");
        Button fEsc = UiKit.filterChip("Escalations");
        UiKit.selectFilter(fAll, fAll, fAlarm, fReminder, fEsc);
        fAll.setOnAction(e -> { typeFilter = "all"; UiKit.selectFilter(fAll, fAll, fAlarm, fReminder, fEsc); reload(); });
        fAlarm.setOnAction(e -> { typeFilter = "alarm"; UiKit.selectFilter(fAlarm, fAll, fAlarm, fReminder, fEsc); reload(); });
        fReminder.setOnAction(e -> { typeFilter = "reminder"; UiKit.selectFilter(fReminder, fAll, fAlarm, fReminder, fEsc); reload(); });
        fEsc.setOnAction(e -> { typeFilter = "escalation"; UiKit.selectFilter(fEsc, fAll, fAlarm, fReminder, fEsc); reload(); });

        Button d7 = UiKit.filterChip("7d");
        Button d30 = UiKit.filterChip("30d");
        Button d90 = UiKit.filterChip("90d");
        UiKit.selectFilter(d30, d7, d30, d90);
        d7.setOnAction(e -> { sinceDays = 7; UiKit.selectFilter(d7, d7, d30, d90); reload(); });
        d30.setOnAction(e -> { sinceDays = 30; UiKit.selectFilter(d30, d7, d30, d90); reload(); });
        d90.setOnAction(e -> { sinceDays = 90; UiKit.selectFilter(d90, d7, d30, d90); reload(); });

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("ghost-btn");
        refresh.setOnAction(e -> reload());

        HBox filters = new HBox(8, fAll, fAlarm, fReminder, fEsc, d7, d30, d90, refresh);
        filters.setAlignment(Pos.CENTER_LEFT);

        VBox detailCard = Panels.card("Detail", detailTitle, detailBox);
        VBox timeline = Panels.card("Timeline", filters, hint, listBox);
        HBox.setHgrow(timeline, Priority.ALWAYS);
        HBox.setHgrow(detailCard, Priority.ALWAYS);

        getChildren().add(new HBox(14, timeline, detailCard));
        reload();
    }

    private void reload() {
        listBox.getChildren().clear();
        try {
            StringBuilder path = new StringBuilder("/api/history?since=" + sinceDays);
            if (!"all".equals(typeFilter)) {
                path.append("&type=").append(typeFilter);
            }
            JsonNode hist = api.get(path.toString());
            int count = 0;
            for (JsonNode item : hist.path("items")) {
                listBox.getChildren().add(row(item));
                count++;
            }
            if (count == 0) {
                hint.setText("");
                listBox.getChildren().add(UiKit.emptyState(
                        "No history yet",
                        "Completed alarms and reminders will show up here."));
            } else {
                hint.setText(count + " items · last " + sinceDays + " days");
            }
        } catch (Exception ex) {
            shell.toastError(ex.getMessage());
        }
    }

    private HBox row(JsonNode item) {
        String kind = item.path("kind").asText("item");
        int ref = item.path("ref_id").asInt();
        String title = item.path("title").asText("");
        String status = item.path("status").asText("");
        String when = item.path("occurred_at").asText(
                item.path("created_at").asText(item.path("timestamp").asText("")));

        Label titleLbl = new Label(prettyKind(kind) + " · " + (title.isBlank() ? "Untitled" : title));
        titleLbl.getStyleClass().add("item-title");
        titleLbl.setWrapText(true);
        Label meta = new Label(UiKit.friendlyDue(when.isBlank() ? null : when)
                + (status.isBlank() ? "" : " · " + status));
        meta.getStyleClass().add("muted");
        VBox text = new VBox(2, titleLbl, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button open = new Button("Open");
        open.getStyleClass().add("secondary-btn");
        open.setOnAction(e -> loadDetail(kind, ref, title, status));

        HBox row = new HBox(12, UiKit.statusBadge(prettyKind(kind)), text, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        row.setOnMouseClicked(ev -> loadDetail(kind, ref, title, status));
        return row;
    }

    private void loadDetail(String kind, int ref, String title, String status) {
        detailBox.getChildren().clear();
        detailTitle.setText((title == null || title.isBlank()) ? prettyKind(kind) : title);
        try {
            JsonNode detail = api.get("/api/history/" + kind + "/" + ref);
            detailBox.getChildren().add(detailLine("Type", prettyKind(kind)));
            if (status != null && !status.isBlank()) {
                detailBox.getChildren().add(detailLine("Status", status));
            }

            boolean escalation = kind != null && kind.toLowerCase(Locale.ROOT).contains("escalat");
            if (escalation) {
                Label why = new Label("Why?");
                why.getStyleClass().add("card-title");
                detailBox.getChildren().add(why);
                String reasoning = detail.path("reasoning").asText("");
                String decidedBy = detail.path("decided_by").asText("");
                String confidence = detail.path("confidence").isMissingNode()
                        ? ""
                        : detail.path("confidence").asText("");
                boolean fallback = detail.path("fallback_used").asBoolean(false)
                        || "rules".equalsIgnoreCase(decidedBy)
                        || decidedBy.toLowerCase(Locale.ROOT).contains("fallback");
                if (!reasoning.isBlank()) {
                    detailBox.getChildren().add(detailLine("Reasoning", reasoning));
                }
                if (!decidedBy.isBlank()) {
                    detailBox.getChildren().add(detailLine("Decided by", decidedBy));
                }
                if (!confidence.isBlank()) {
                    detailBox.getChildren().add(detailLine("Confidence", confidence));
                }
                detailBox.getChildren().add(detailLine(
                        "Fallback",
                        fallback ? "Rule-based fallback / ladder used" : "LLM decision"));
                String method = detail.path("method").asText("");
                if (!method.isBlank()) {
                    detailBox.getChildren().add(detailLine("Method", method));
                }
                Button ack = new Button("Acknowledge");
                ack.getStyleClass().add("secondary-btn");
                ack.setOnAction(e -> {
                    try {
                        api.post("/api/escalations/acknowledge", Map.of("via", "history"));
                        shell.toastOk("Escalation acknowledged.");
                        reload();
                    } catch (Exception ex) {
                        shell.toastError(UiKit.userError(ex));
                    }
                });
                detailBox.getChildren().add(Panels.actionRow(ack));
            }

            Iterator<Map.Entry<String, JsonNode>> fields = detail.fields();
            int shown = 0;
            while (fields.hasNext() && shown < 14) {
                Map.Entry<String, JsonNode> e = fields.next();
                String key = e.getKey();
                if (key == null || key.equalsIgnoreCase("id") || key.equalsIgnoreCase("raw")) {
                    continue;
                }
                if (escalation && (key.equals("reasoning") || key.equals("decided_by")
                        || key.equals("confidence") || key.equals("fallback_used") || key.equals("method"))) {
                    continue;
                }
                String value = friendlyNode(e.getValue());
                if (value.isBlank()) {
                    continue;
                }
                detailBox.getChildren().add(detailLine(humanizeKey(key), value));
                shown++;
            }
            if (shown == 0 && !escalation) {
                detailBox.getChildren().add(UiKit.muted("No extra detail for this item."));
            }
        } catch (Exception ex) {
            detailBox.getChildren().add(UiKit.muted(ex.getMessage()));
            shell.toastError(ex.getMessage());
        }
    }

    private static HBox detailLine(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        l.setMinWidth(110);
        Label v = new Label(value);
        v.getStyleClass().add("item-title");
        v.setWrapText(true);
        HBox.setHgrow(v, Priority.ALWAYS);
        HBox row = new HBox(10, l, v);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private static String prettyKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "Item";
        }
        String k = kind.toLowerCase(Locale.ROOT);
        if (k.contains("alarm")) {
            return "Alarm";
        }
        if (k.contains("remind") || k.contains("task") || k.contains("event")) {
            return "Reminder";
        }
        if (k.contains("escalat")) {
            return "Escalation";
        }
        return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    }

    private static String humanizeKey(String key) {
        String k = key.replace('_', ' ').trim();
        if (k.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    private static String friendlyNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            String t = node.asText("");
            if (t.contains("T") && t.length() >= 16) {
                return UiKit.friendlyDue(t);
            }
            return t;
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            node.forEach(n -> {
                String part = friendlyNode(n);
                if (!part.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append(", ");
                    }
                    sb.append(part);
                }
            });
            return sb.toString();
        }
        return node.toString();
    }
}
