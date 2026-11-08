package com.riseup.gui.ui.components;

import com.riseup.gui.ui.MainShell;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Day/time sheets over the content area only. Nav/header stay untouched;
 * no full-window snapshot (that was duplicating the sidebar in the blur).
 */
public final class DateTimePickers {
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private DateTimePickers() {}

    public static Optional<LocalDate> pickDate(Window owner, LocalDate initial) {
        MonthCalendar calendar = new MonthCalendar(initial == null ? LocalDate.now() : initial);
        Label preview = new Label(formatDay(calendar.getSelected()));
        preview.getStyleClass().add("picker-preview");
        calendar.setOnChange(d -> preview.setText(formatDay(d)));

        VBox body = new VBox(12, preview, calendar);
        body.setFillWidth(true);

        return showSheet(owner, "Choose a day", "Tap a date in the calendar", body, "Set day",
                calendar::getSelected);
    }

    public static Optional<LocalTime> pickTime(Window owner, LocalTime initial) {
        ClockTimePicker clock = new ClockTimePicker();
        if (initial != null) {
            clock.setTime(initial);
        }
        clock.setMaxWidth(360);

        return showSheet(owner, "Set time", "Scroll, tap, or click a number to type", clock, "Set time",
                clock::toLocalTime);
    }

    public static String formatDay(LocalDate d) {
        return d == null ? "Pick a day" : d.format(DAY_FMT);
    }

    public static String formatTime(LocalTime t) {
        return t == null ? "Pick a time" : t.format(TIME_FMT);
    }

    public static String toDueIso(LocalDate day, LocalTime time) {
        return LocalDateTime.of(day, time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private static <T> Optional<T> showSheet(
            Window owner,
            String titleText,
            String hintText,
            Node body,
            String confirmLabel,
            Supplier<T> valueSupplier) {

        MainShell shell = findShell(owner);
        if (shell == null) {
            return Optional.empty();
        }
        StackPane host = shell.modalLayer();

        AtomicReference<T> chosen = new AtomicReference<>();
        AtomicBoolean confirmed = new AtomicBoolean(false);
        Object loopKey = new Object();

        Label title = new Label(titleText);
        title.getStyleClass().add("picker-title");
        Label hint = UiKit.muted(hintText);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setCancelButton(true);

        Button done = new Button(confirmLabel);
        done.getStyleClass().add("primary-btn");
        done.setDefaultButton(true);

        HBox actions = new HBox(10, cancel, done);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("picker-actions");

        VBox sheet = new VBox(14, title, hint, body, actions);
        sheet.setPadding(new Insets(20));
        sheet.getStyleClass().addAll("list-panel", "picker-sheet");
        sheet.setFillWidth(true);
        sheet.setMaxWidth(420);
        sheet.setPrefWidth(400);
        sheet.setOnMouseClicked(e -> e.consume());

        ScrollPane scroller = new ScrollPane(sheet);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.getStyleClass().add("picker-scroll");
        scroller.setPrefWidth(420);
        scroller.setMaxWidth(420);
        scroller.setMinWidth(300);
        scroller.setMinHeight(Region.USE_PREF_SIZE);
        scroller.setMaxHeight(Region.USE_PREF_SIZE);
        scroller.setOnMouseClicked(e -> e.consume());

        Region frost = new Region();
        frost.getStyleClass().add("picker-frost");
        frost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Center the sheet in the content column only. Nav/header are outside this layer.
        StackPane stage = new StackPane(scroller);
        stage.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        stage.setPickOnBounds(false);
        StackPane.setAlignment(scroller, Pos.CENTER);
        StackPane.setMargin(scroller, new Insets(24));

        StackPane overlay = new StackPane(frost, stage);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(stage, Pos.CENTER);

        Runnable close = () -> {
            host.getChildren().clear();
            host.setMouseTransparent(true);
            host.setVisible(false);
            shell.setContentBlurred(false);
            Platform.exitNestedEventLoop(loopKey, null);
        };

        cancel.setOnAction(e -> close.run());
        frost.setOnMouseClicked(e -> close.run());
        done.setOnAction(e -> {
            chosen.set(valueSupplier.get());
            confirmed.set(true);
            close.run();
        });

        double maxSheetH = Math.max(280, host.getHeight() > 0 ? host.getHeight() - 48 : 520);
        shell.setContentBlurred(true);
        host.setVisible(true);
        host.setMouseTransparent(false);
        host.getChildren().setAll(overlay);
        Platform.runLater(() -> fitScrollerToContent(scroller, sheet, maxSheetH));

        Platform.enterNestedEventLoop(loopKey);
        return confirmed.get() ? Optional.ofNullable(chosen.get()) : Optional.empty();
    }

    private static MainShell findShell(Window owner) {
        if (owner == null || owner.getScene() == null) {
            return null;
        }
        Node root = owner.getScene().getRoot();
        return root instanceof MainShell shell ? shell : null;
    }

    private static void fitScrollerToContent(ScrollPane scroller, VBox sheet, double maxH) {
        scroller.applyCss();
        sheet.applyCss();
        sheet.layout();
        double width = Math.min(420, Math.max(300, scroller.getWidth() > 0 ? scroller.getWidth() : 400));
        double contentH = sheet.prefHeight(width);
        double h = Math.min(Math.max(contentH + 6, 200), maxH);
        scroller.setPrefHeight(h);
        scroller.setMaxHeight(h);
        scroller.setPrefWidth(Math.min(440, width + 8));
        scroller.setMaxWidth(440);
    }
}
