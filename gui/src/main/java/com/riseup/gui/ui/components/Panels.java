package com.riseup.gui.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Shared panel chrome for page sections. */
public final class Panels {
    private Panels() {}

    public static VBox pageHeader(String kicker, String title, String subtitle) {
        VBox header = new VBox(4);
        header.getStyleClass().add("page-header");
        if (kicker != null && !kicker.isBlank()) {
            Label k = new Label(kicker.toUpperCase());
            k.getStyleClass().add("page-kicker");
            header.getChildren().add(k);
        }
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        header.getChildren().add(t);
        if (subtitle != null && !subtitle.isBlank()) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("page-subtitle");
            s.setWrapText(true);
            header.getChildren().add(s);
        }
        return header;
    }

    public static VBox card(String heading, Node... body) {
        return card(heading, null, body);
    }

    public static VBox card(String heading, String subtitle, Node... body) {
        VBox box = new VBox(10);
        box.getStyleClass().add("list-panel");
        if (heading != null) {
            Label h = new Label(heading);
            h.getStyleClass().add("card-title");
            if (subtitle != null && !subtitle.isBlank()) {
                Label sub = new Label(subtitle);
                sub.getStyleClass().add("muted");
                VBox titles = new VBox(2, h, sub);
                box.getChildren().add(titles);
            } else {
                box.getChildren().add(h);
            }
        }
        box.getChildren().addAll(body);
        return box;
    }

    public static VBox heroCard(String heading, Node... body) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-hero");
        if (heading != null) {
            Label h = new Label(heading);
            h.getStyleClass().add("card-title");
            box.getChildren().add(h);
        }
        box.getChildren().addAll(body);
        return box;
    }

    public static HBox actionRow(Node... nodes) {
        HBox row = new HBox(10);
        row.getStyleClass().add("action-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nodes);
        return row;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
