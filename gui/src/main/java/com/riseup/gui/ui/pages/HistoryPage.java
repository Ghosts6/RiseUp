package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class HistoryPage extends VBox {
    private final ApiClient api;
    private final MainShell shell;
    private final VBox listBox = new VBox(8);
    private final TextArea detail = new TextArea();
    private final Label hint = UiKit.muted("");

    public HistoryPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Archive",
                "History",
                "Browse past activity — click a row to inspect the details."));

        detail.setEditable(false);
        detail.setPrefRowCount(10);
        detail.setPromptText("Select an item to see details…");

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("primary-btn");
        refresh.setOnAction(e -> reload());

        getChildren().add(Panels.card("Timeline",
                Panels.actionRow(refresh),
                hint,
                listBox,
                UiKit.labeled("Detail", detail)));
        reload();
    }

    private void reload() {
        listBox.getChildren().clear();
        try {
            JsonNode hist = api.get("/api/history");
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
                hint.setText(count + " items — click one to inspect");
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

        Label titleLbl = new Label(kind + " · " + title);
        titleLbl.getStyleClass().add("item-title");
        titleLbl.setWrapText(true);
        Label meta = new Label("#" + ref);
        meta.getStyleClass().add("muted");
        VBox text = new VBox(2, titleLbl, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button open = new Button("Open");
        open.getStyleClass().add("secondary-btn");
        open.setOnAction(e -> loadDetail(kind, ref));

        HBox row = new HBox(12, UiKit.statusBadge(status), text, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        row.setOnMouseClicked(ev -> {
            if (ev.getClickCount() >= 1) {
                loadDetail(kind, ref);
            }
        });
        return row;
    }

    private void loadDetail(String kind, int ref) {
        try {
            detail.setText(api.get("/api/history/" + kind + "/" + ref).toPrettyString());
        } catch (Exception ex) {
            detail.setText(ex.getMessage());
            shell.toastError(ex.getMessage());
        }
    }
}
