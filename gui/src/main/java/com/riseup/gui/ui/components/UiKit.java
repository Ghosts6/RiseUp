package com.riseup.gui.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;

/** Small UX helpers shared across pages. */
public final class UiKit {
    private static final DateTimeFormatter DUE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private UiKit() {}

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    public static VBox emptyState(String title, String hint) {
        VBox box = new VBox(6);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        Label h = new Label(hint);
        h.getStyleClass().add("muted");
        h.setWrapText(true);
        box.getChildren().addAll(t, h);
        return box;
    }

    public static Label statusBadge(String status) {
        Label badge = new Label(status == null ? "None" : status);
        badge.getStyleClass().add("badge");
        String s = status == null ? "" : status.toLowerCase();
        if (s.contains("active") || s.contains("pending") || s.equals("ok") || s.equals("scheduled")) {
            badge.getStyleClass().add("badge-ok");
        } else if (s.contains("ring") || s.contains("escalat") || s.contains("overdue")) {
            badge.getStyleClass().add("badge-warn");
        } else if (s.contains("fail") || s.contains("error") || s.contains("miss")) {
            badge.getStyleClass().add("badge-err");
        } else if (s.contains("done") || s.contains("complete") || s.contains("dismiss")) {
            badge.getStyleClass().add("badge-ok");
        } else {
            badge.getStyleClass().add("badge-warn");
        }
        return badge;
    }

    public static Button chip(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("chip");
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Button filterChip(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("filter-chip");
        return b;
    }

    public static void selectFilter(Button selected, Button... all) {
        for (Button b : all) {
            b.getStyleClass().remove("active");
        }
        selected.getStyleClass().add("active");
    }

    public static VBox labeled(String label, Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        VBox box = new VBox(4, l, field);
        return box;
    }

    public static TextField textField(String prompt, String value) {
        TextField f = new TextField(value == null ? "" : value);
        f.setPromptText(prompt);
        f.getStyleClass().add("field");
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    public static void onEnter(TextInputControl field, Runnable action) {
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                action.run();
            }
        });
    }

    /** Prefer opening the ComboBox popup below the control (Apple-style menus). */
    public static <T> ComboBox<T> openComboDown(ComboBox<T> combo) {
        if (combo == null) {
            return null;
        }
        combo.setOnShown(e -> Platform.runLater(() -> {
            Bounds b = combo.localToScreen(combo.getBoundsInLocal());
            if (b == null) {
                return;
            }
            for (Window w : Window.getWindows()) {
                if (!(w instanceof PopupWindow pw) || !pw.isShowing()) {
                    continue;
                }
                Node owner = pw.getOwnerNode();
                if (owner != combo && (owner == null || !isUnder(combo, owner))) {
                    continue;
                }
                pw.setX(b.getMinX());
                pw.setY(b.getMaxY() + 4);
                if (pw.getWidth() > 0 && pw.getWidth() < b.getWidth()) {
                    // keep visual alignment with the field
                }
                break;
            }
        }));
        return combo;
    }

    private static boolean isUnder(Node ancestor, Node node) {
        Node cur = node;
        while (cur != null) {
            if (cur == ancestor) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    public static boolean confirm(Node owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            alert.initOwner(owner.getScene().getWindow());
        }
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void runBusy(Button button, String busyText, Runnable work) {
        String original = button.getText();
        boolean wasDisabled = button.isDisable();
        button.setDisable(true);
        button.setText(busyText);
        try {
            work.run();
        } finally {
            button.setText(original);
            button.setDisable(wasDisabled);
        }
    }

    public static String dueInMinutes(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES).format(DUE_FMT);
    }

    public static String dueAt(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).format(DUE_FMT);
    }

    public static String tonight() {
        LocalDateTime candidate = LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 0));
        if (!candidate.isAfter(LocalDateTime.now())) {
            candidate = candidate.plusDays(1);
        }
        return candidate.format(DUE_FMT);
    }

    public static String tomorrowMorning() {
        return LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0)).format(DUE_FMT);
    }

    public static String friendlyDue(String isoOrLocal) {
        if (isoOrLocal == null || isoOrLocal.isBlank()) {
            return "No due time";
        }
        try {
            LocalDateTime dt = parseFlexibleDateTime(isoOrLocal);
            LocalDateTime now = LocalDateTime.now();
            if (dt.toLocalDate().equals(now.toLocalDate())) {
                return "Today " + dt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).format(TIME_FMT);
            }
            if (dt.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
                return "Tomorrow " + dt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).format(TIME_FMT);
            }
            return dt.format(DateTimeFormatter.ofPattern("EEE MMM d · HH:mm"));
        } catch (Exception e) {
            return isoOrLocal.replace('T', ' ');
        }
    }

    public static LocalDateTime parseFlexibleDateTime(String raw) {
        String s = raw.trim().replace("Z", "");
        if (s.length() > 19) {
            s = s.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(s, DUE_FMT);
        } catch (DateTimeParseException ignored) {
        }
        if (s.contains(" ")) {
            return LocalDateTime.parse(s.replace(' ', 'T').substring(0, Math.min(16, s.length())), DUE_FMT);
        }
        throw new DateTimeParseException("Invalid datetime", raw, 0);
    }

    public static boolean isValidTime(String hhmm) {
        try {
            LocalTime.parse(hhmm.trim(), TIME_FMT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static HBox grow(Node left, Node... right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(left);
        row.getChildren().add(spacer);
        row.getChildren().addAll(right);
        return row;
    }

    public static void showToast(StackPane host, String message, boolean error) {
        if (host == null) {
            return;
        }
        Label toast = new Label(message);
        toast.getStyleClass().addAll("toast", error ? "toast-error" : "toast-ok");
        toast.setWrapText(true);
        toast.setMaxWidth(420);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new javafx.geometry.Insets(0, 0, 28, 0));
        host.getChildren().removeIf(n -> n.getStyleClass().contains("toast"));
        host.getChildren().add(toast);
        toast.setOpacity(0);
        FadeTransition in = new FadeTransition(Duration.millis(160), toast);
        in.setToValue(1);
        in.play();
        PauseTransition hold = new PauseTransition(Duration.seconds(error ? 4.2 : 2.6));
        hold.setOnFinished(e -> {
            FadeTransition out = new FadeTransition(Duration.millis(220), toast);
            out.setToValue(0);
            out.setOnFinished(ev -> host.getChildren().remove(toast));
            out.play();
        });
        hold.play();
    }

    public static void withFeedback(Consumer<String> onError, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            onError.accept(userError(ex));
        }
    }

    /** Prefer ApiException.userMessage(); never show raw JSON traces. */
    public static String userError(Throwable ex) {
        if (ex instanceof com.riseup.gui.api.ApiException api) {
            return api.userMessage();
        }
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return "Something went wrong.";
        }
        if (msg.contains("HTTP ") && msg.contains("{")) {
            int brace = msg.indexOf('{');
            String json = msg.substring(brace);
            try {
                var n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                if (n.hasNonNull("detail")) {
                    return new com.riseup.gui.api.ApiException(
                            n.get("detail").asText(), 0, null, n.get("detail").asText(), null)
                            .userMessage();
                }
            } catch (Exception ignored) {
            }
            return "That action isn’t allowed right now.";
        }
        return msg;
    }
}
