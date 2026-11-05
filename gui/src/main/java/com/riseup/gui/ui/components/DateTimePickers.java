package com.riseup.gui.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
 * In-window day/time sheets. Overlay matches the owner window so content never
 * overflows the page; calendar is inline (no nested DatePicker popup).
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

        return showSheet(owner, "Choose a day", "Tap a date — stays inside the window", body, "Set day",
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

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);

        AtomicReference<T> chosen = new AtomicReference<>();
        AtomicBoolean confirmed = new AtomicBoolean(false);

        Label title = new Label(titleText);
        title.getStyleClass().add("picker-title");
        Label hint = UiKit.muted(hintText);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());

        Button done = new Button(confirmLabel);
        done.getStyleClass().add("primary-btn");
        done.setDefaultButton(true);
        done.setOnAction(e -> {
            chosen.set(valueSupplier.get());
            confirmed.set(true);
            stage.close();
        });

        HBox actions = new HBox(10, cancel, done);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("picker-actions");

        VBox sheet = new VBox(14, title, hint, body, actions);
        sheet.setPadding(new Insets(20));
        sheet.getStyleClass().addAll("list-panel", "picker-sheet");
        sheet.setFillWidth(true);
        sheet.setMaxWidth(420);
        sheet.setPrefWidth(400);

        ScrollPane scroller = new ScrollPane(sheet);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.getStyleClass().add("picker-scroll");
        scroller.setMaxWidth(440);
        // Critical: don't let StackPane stretch this to full window height
        // (that pinned the sheet to the top of a tall scroll viewport).
        scroller.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        scroller.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        Region frost = new Region();
        frost.getStyleClass().add("picker-frost");
        frost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        frost.setOnMouseClicked(e -> stage.close());

        sheet.setOnMouseClicked(e -> e.consume());
        scroller.setOnMouseClicked(e -> e.consume());

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("root");
        Node backdrop = buildBlurredBackdrop(owner);
        if (backdrop != null) {
            overlay.getChildren().add(backdrop);
        }
        overlay.getChildren().addAll(frost, scroller);
        StackPane.setAlignment(scroller, Pos.CENTER);
        StackPane.setMargin(scroller, new Insets(28));
        if (owner != null && owner.getScene() != null
                && owner.getScene().getRoot().getStyleClass().contains("dark")) {
            overlay.getStyleClass().add("dark");
        }

        Scene scene = new Scene(overlay);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        var css = DateTimePickers.class.getResource("/css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);

        final double[] maxSheetH = {520};
        if (owner != null) {
            stage.setX(owner.getX());
            stage.setY(owner.getY());
            stage.setWidth(Math.max(320, owner.getWidth()));
            stage.setHeight(Math.max(320, owner.getHeight()));
            maxSheetH[0] = Math.max(280, owner.getHeight() - 56);
            owner.xProperty().addListener((o, a, b) -> stage.setX(b.doubleValue()));
            owner.yProperty().addListener((o, a, b) -> stage.setY(b.doubleValue()));
            owner.widthProperty().addListener((o, a, b) -> {
                stage.setWidth(Math.max(320, b.doubleValue()));
                maxSheetH[0] = Math.max(280, owner.getHeight() - 56);
                fitScrollerToContent(scroller, sheet, maxSheetH[0]);
            });
            owner.heightProperty().addListener((o, a, b) -> {
                stage.setHeight(Math.max(320, b.doubleValue()));
                maxSheetH[0] = Math.max(280, b.doubleValue() - 56);
                fitScrollerToContent(scroller, sheet, maxSheetH[0]);
            });
        } else {
            stage.setWidth(460);
            stage.setHeight(560);
            maxSheetH[0] = 520;
        }

        stage.setOnShown(e -> Platform.runLater(() -> fitScrollerToContent(scroller, sheet, maxSheetH[0])));
        stage.showAndWait();
        return confirmed.get() ? Optional.ofNullable(chosen.get()) : Optional.empty();
    }

    /** Size the scroll host to the sheet so StackPane can truly center it. */
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

    private static Node buildBlurredBackdrop(Window owner) {
        if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) {
            return null;
        }
        try {
            Node root = owner.getScene().getRoot();
            double w = Math.max(1, owner.getWidth());
            double h = Math.max(1, owner.getHeight());
            WritableImage snap = root.snapshot(null, null);
            ImageView bg = new ImageView(snap);
            bg.setMouseTransparent(true);
            bg.setEffect(new GaussianBlur(22));
            bg.setFitWidth(w);
            bg.setFitHeight(h);
            bg.setPreserveRatio(false);
            bg.setSmooth(true);
            return bg;
        } catch (Exception e) {
            return null;
        }
    }
}
