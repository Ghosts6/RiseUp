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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecommendationsPage extends VBox {
    private final ApiClient api;
    private final MainShell shell;
    private final VBox listBox = new VBox(0);
    private final Label hint = UiKit.muted("");
    private final List<JsonNode> cache = new ArrayList<>();

    public RecommendationsPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Suggestions",
                "Recommendations",
                "Generate coaching ideas, then accept or dismiss each one."));

        Button generate = new Button("Generate suggestions");
        generate.getStyleClass().add("primary-btn");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("ghost-btn");

        generate.setOnAction(e -> UiKit.runBusy(generate, "Generating…", () -> {
            try {
                api.post("/api/recommendations/generate", Map.of());
                shell.toastOk("Fresh recommendations ready.");
                reload();
            } catch (Exception ex) {
                shell.toastError(ex.getMessage());
            }
        }));
        refresh.setOnAction(e -> reload());

        getChildren().add(Panels.card("Inbox",
                Panels.actionRow(generate, refresh),
                hint,
                listBox));
        reload();
    }

    private void reload() {
        try {
            cache.clear();
            for (JsonNode r : api.get("/api/recommendations")) {
                cache.add(r);
            }
            render();
        } catch (Exception ex) {
            shell.toastError(ex.getMessage());
        }
    }

    private void render() {
        listBox.getChildren().clear();
        if (cache.isEmpty()) {
            hint.setText("");
            listBox.getChildren().add(UiKit.emptyState(
                    "No recommendations yet",
                    "Click “Generate suggestions” to ask the coach for ideas."));
            return;
        }
        hint.setText(cache.size() + " item(s)");
        for (JsonNode r : cache) {
            listBox.getChildren().add(row(r));
        }
    }

    private HBox row(JsonNode r) {
        int id = r.path("id").asInt();
        String title = r.path("title").asText("Suggestion");
        String rationale = r.path("rationale").asText("");
        String status = r.path("status").asText("pending");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("item-title");
        titleLbl.setWrapText(true);
        Label meta = new Label(rationale.isBlank() ? status : rationale);
        meta.getStyleClass().add("muted");
        meta.setWrapText(true);
        VBox text = new VBox(4, titleLbl, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button accept = new Button("Accept");
        accept.getStyleClass().add("primary-btn");
        Button dismiss = new Button("Dismiss");
        dismiss.getStyleClass().add("danger-btn");

        boolean closed = status.toLowerCase().contains("accept")
                || status.toLowerCase().contains("dismiss");
        accept.setDisable(closed);
        dismiss.setDisable(closed);

        accept.setOnAction(e -> act(id, "accept", "Accepted."));
        dismiss.setOnAction(e -> act(id, "dismiss", "Dismissed."));

        HBox actions = new HBox(8, accept, dismiss);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, UiKit.statusBadge(status), text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private void act(int id, String action, String okMsg) {
        try {
            api.post("/api/recommendations/" + id + "/" + action, Map.of());
            shell.toastOk(okMsg);
            reload();
        } catch (Exception ex) {
            shell.toastError(ex.getMessage());
        }
    }
}
