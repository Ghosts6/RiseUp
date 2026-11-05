package com.riseup.gui.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Apple Clock–style hour / minute wheels + tap-to-swap AM/PM.
 * Click the selected value to type; scroll moves one step at a controllable pace.
 */
public class ClockTimePicker extends VBox {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final WheelColumn hourWheel;
    private final WheelColumn minuteWheel;
    private final AmPmToggle ampmToggle;
    private final Label preview = new Label();
    private Runnable onChange = () -> {};

    public ClockTimePicker() {
        getStyleClass().add("clock-picker");
        setSpacing(12);
        setAlignment(Pos.CENTER);
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        List<String> hours = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(String::valueOf).toList();
        List<String> minutes = java.util.stream.IntStream.range(0, 60)
                .mapToObj(m -> String.format("%02d", m)).toList();

        hourWheel = new WheelColumn("HOUR", hours, 6, false);
        minuteWheel = new WheelColumn("MIN", minutes, 30, true);
        ampmToggle = new AmPmToggle();

        hourWheel.setOnChanged(this::fireChange);
        minuteWheel.setOnChanged(this::fireChange);
        ampmToggle.setOnChanged(this::fireChange);

        Label colon = new Label(":");
        colon.getStyleClass().add("clock-colon");
        StackPane colonBox = new StackPane(colon);
        colonBox.setAlignment(Pos.CENTER);
        colonBox.setMinWidth(16);

        GridPane wheels = new GridPane();
        wheels.getStyleClass().add("clock-wheels");
        wheels.setHgap(8);
        wheels.setAlignment(Pos.CENTER);
        ColumnConstraints c0 = growCol();
        ColumnConstraints c1 = new ColumnConstraints(20);
        ColumnConstraints c2 = growCol();
        ColumnConstraints c3 = growCol();
        wheels.getColumnConstraints().addAll(c0, c1, c2, c3);
        wheels.add(hourWheel, 0, 0);
        wheels.add(colonBox, 1, 0);
        wheels.add(minuteWheel, 2, 0);
        wheels.add(ampmToggle, 3, 0);
        GridPane.setHgrow(hourWheel, Priority.ALWAYS);
        GridPane.setHgrow(minuteWheel, Priority.ALWAYS);
        GridPane.setHgrow(ampmToggle, Priority.ALWAYS);

        StackPane stage = new StackPane();
        stage.getStyleClass().add("clock-stage");
        stage.setMaxWidth(Double.MAX_VALUE);
        Rectangle highlight = new Rectangle();
        highlight.getStyleClass().add("clock-highlight");
        highlight.setManaged(false);
        highlight.setMouseTransparent(true);
        highlight.widthProperty().bind(stage.widthProperty().subtract(20));
        highlight.setHeight(56);
        highlight.setArcWidth(16);
        highlight.setArcHeight(16);
        StackPane.setAlignment(highlight, Pos.CENTER);
        stage.getChildren().addAll(highlight, wheels);

        preview.getStyleClass().add("clock-preview");
        getChildren().addAll(stage, preview);
        updatePreview();
    }

    private static ColumnConstraints growCol() {
        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(Priority.ALWAYS);
        c.setPercentWidth(30);
        c.setMinWidth(72);
        return c;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public String getTime24() {
        return toLocalTime().format(HHMM);
    }

    public LocalTime toLocalTime() {
        int hour12 = Integer.parseInt(hourWheel.getValue());
        int minute = Integer.parseInt(minuteWheel.getValue());
        boolean pm = ampmToggle.isPm();
        int hour24 = hour12 == 12 ? (pm ? 12 : 0) : (pm ? hour12 + 12 : hour12);
        return LocalTime.of(hour24, minute);
    }

    public String getDisplayText() {
        LocalTime t = toLocalTime();
        int h = t.getHour() % 12;
        if (h == 0) {
            h = 12;
        }
        return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " AM" : " PM");
    }

    public void setTime(LocalTime time) {
        int hour24 = time.getHour();
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        hourWheel.setValue(String.valueOf(hour12));
        minuteWheel.setValue(String.format("%02d", time.getMinute()));
        ampmToggle.setPm(hour24 >= 12);
        updatePreview();
    }

    public void setTime24(String hhmm) {
        try {
            setTime(LocalTime.parse(hhmm, HHMM));
        } catch (Exception e) {
            setTime(LocalTime.of(7, 30));
        }
    }

    private void fireChange() {
        updatePreview();
        onChange.run();
    }

    private void updatePreview() {
        preview.setText(getDisplayText());
    }

    /** Tap-only AM/PM control (same column slot as a wheel). */
    private static final class AmPmToggle extends VBox {
        private boolean pm;
        private final Label value = new Label("AM");
        private Runnable onChanged = () -> {};

        AmPmToggle() {
            getStyleClass().add("clock-wheel-col");
            setAlignment(Pos.CENTER);
            setFillWidth(true);
            setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(this, Priority.ALWAYS);

            Label cap = new Label("AM/PM");
            cap.getStyleClass().add("clock-wheel-caption");

            Label spacerTop = new Label(" ");
            spacerTop.getStyleClass().addAll("clock-num", "clock-num-side");
            spacerTop.setMaxWidth(Double.MAX_VALUE);
            spacerTop.setMinHeight(32);
            Label spacerBot = new Label(" ");
            spacerBot.getStyleClass().addAll("clock-num", "clock-num-side");
            spacerBot.setMaxWidth(Double.MAX_VALUE);
            spacerBot.setMinHeight(32);

            value.getStyleClass().addAll("clock-num", "clock-num-current", "ampm-toggle");
            value.setMaxWidth(Double.MAX_VALUE);
            value.setAlignment(Pos.CENTER);
            value.setOnMouseClicked(e -> swap());
            Tooltip.install(value, new Tooltip("Click to switch AM ↔ PM"));

            Button upPad = new Button(" ");
            upPad.getStyleClass().add("clock-step-btn");
            upPad.setMaxWidth(Double.MAX_VALUE);
            upPad.setOpacity(0);
            upPad.setMouseTransparent(true);
            Button downPad = new Button(" ");
            downPad.getStyleClass().add("clock-step-btn");
            downPad.setMaxWidth(Double.MAX_VALUE);
            downPad.setOpacity(0);
            downPad.setMouseTransparent(true);

            VBox dial = new VBox(2, spacerTop, value, spacerBot);
            dial.getStyleClass().addAll("clock-dial", "ampm-dial");
            dial.setAlignment(Pos.CENTER);
            dial.setFillWidth(true);
            dial.setMaxWidth(Double.MAX_VALUE);
            dial.setOnMouseClicked(e -> swap());
            Tooltip.install(dial, new Tooltip("Click to switch AM ↔ PM"));

            getChildren().addAll(cap, upPad, dial, downPad);
            setOnMouseClicked(e -> swap());
            Tooltip.install(this, new Tooltip("Click to switch AM ↔ PM"));
        }

        void setOnChanged(Runnable r) {
            this.onChanged = r == null ? () -> {} : r;
        }

        boolean isPm() {
            return pm;
        }

        void setPm(boolean pm) {
            this.pm = pm;
            value.setText(pm ? "PM" : "AM");
        }

        private void swap() {
            setPm(!pm);
            onChanged.run();
        }
    }

    private static final class WheelColumn extends VBox {
        /** Softer threshold — one notch feels snappy but still controllable. */
        private static final double SCROLL_STEP_PX = 28;
        private static final long SCROLL_COOLDOWN_MS = 85;

        private final List<String> values;
        private final boolean zeroPad;
        private int index;
        private final Label prev = new Label();
        private final Label current = new Label();
        private final Label next = new Label();
        private final StackPane currentSlot = new StackPane();
        private final TextField editor = new TextField();
        private Runnable onChanged = () -> {};
        private double scrollAccum;
        private long nextScrollAllowedAt;
        private boolean editing;

        WheelColumn(String caption, List<String> values, int initialIndex, boolean zeroPad) {
            this.values = values;
            this.zeroPad = zeroPad;
            this.index = Math.max(0, Math.min(initialIndex, values.size() - 1));
            getStyleClass().add("clock-wheel-col");
            setAlignment(Pos.CENTER);
            setFillWidth(true);
            setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(this, Priority.ALWAYS);

            Label cap = new Label(caption);
            cap.getStyleClass().add("clock-wheel-caption");

            Button up = new Button("▴");
            up.getStyleClass().add("clock-step-btn");
            up.setMaxWidth(Double.MAX_VALUE);
            up.setOnAction(e -> {
                if (!editing) {
                    step(-1);
                }
            });

            Button down = new Button("▾");
            down.getStyleClass().add("clock-step-btn");
            down.setMaxWidth(Double.MAX_VALUE);
            down.setOnAction(e -> {
                if (!editing) {
                    step(1);
                }
            });

            prev.getStyleClass().addAll("clock-num", "clock-num-side");
            current.getStyleClass().addAll("clock-num", "clock-num-current");
            next.getStyleClass().addAll("clock-num", "clock-num-side");
            prev.setMaxWidth(Double.MAX_VALUE);
            current.setMaxWidth(Double.MAX_VALUE);
            next.setMaxWidth(Double.MAX_VALUE);
            prev.setAlignment(Pos.CENTER);
            current.setAlignment(Pos.CENTER);
            next.setAlignment(Pos.CENTER);
            prev.setOnMouseClicked(e -> {
                if (!editing) {
                    step(-1);
                }
            });
            next.setOnMouseClicked(e -> {
                if (!editing) {
                    step(1);
                }
            });

            editor.getStyleClass().add("clock-edit-field");
            editor.setAlignment(Pos.CENTER);
            editor.setVisible(false);
            editor.setManaged(false);
            editor.setOnAction(e -> commitEdit());
            editor.focusedProperty().addListener((o, was, focused) -> {
                if (was && !focused && editing) {
                    commitEdit();
                }
            });
            editor.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    e.consume();
                }
            });

            currentSlot.getChildren().addAll(current, editor);
            currentSlot.setAlignment(Pos.CENTER);
            currentSlot.setMaxWidth(Double.MAX_VALUE);
            current.setOnMouseClicked(e -> {
                if (!editing) {
                    startEdit();
                    e.consume();
                }
            });
            Tooltip.install(current, new Tooltip("Click to type a value"));

            VBox dial = new VBox(2, prev, currentSlot, next);
            dial.getStyleClass().add("clock-dial");
            dial.setAlignment(Pos.CENTER);
            dial.setFillWidth(true);
            dial.setMaxWidth(Double.MAX_VALUE);

            getChildren().addAll(cap, up, dial, down);
            addEventFilter(ScrollEvent.SCROLL, this::onScroll);
            render();
        }

        private void onScroll(ScrollEvent e) {
            e.consume();
            if (editing) {
                return;
            }
            double dy = e.getDeltaY();
            if (dy == 0) {
                return;
            }
            if (scrollAccum != 0 && Math.signum(scrollAccum) != Math.signum(dy)) {
                scrollAccum = 0;
            }
            scrollAccum += dy;
            long now = System.currentTimeMillis();
            if (now < nextScrollAllowedAt) {
                if (Math.abs(scrollAccum) > SCROLL_STEP_PX) {
                    scrollAccum = Math.signum(scrollAccum) * (SCROLL_STEP_PX * 0.45);
                }
                return;
            }
            if (Math.abs(scrollAccum) < SCROLL_STEP_PX) {
                return;
            }
            step(scrollAccum > 0 ? -1 : 1);
            scrollAccum = 0;
            nextScrollAllowedAt = now + SCROLL_COOLDOWN_MS;
        }

        private void startEdit() {
            editing = true;
            editor.setText(values.get(index));
            editor.setVisible(true);
            editor.setManaged(true);
            current.setVisible(false);
            current.setManaged(false);
            editor.requestFocus();
            editor.selectAll();
        }

        private void commitEdit() {
            if (!editing) {
                return;
            }
            String raw = editor.getText() == null ? "" : editor.getText().trim();
            Integer parsed = parseTyped(raw);
            if (parsed != null) {
                String key = zeroPad ? String.format("%02d", parsed) : String.valueOf(parsed);
                int i = values.indexOf(key);
                if (i >= 0) {
                    index = i;
                }
            }
            finishEdit();
            render();
            onChanged.run();
        }

        private void cancelEdit() {
            if (!editing) {
                return;
            }
            finishEdit();
            render();
        }

        private void finishEdit() {
            editing = false;
            editor.setVisible(false);
            editor.setManaged(false);
            current.setVisible(true);
            current.setManaged(true);
        }

        private Integer parseTyped(String raw) {
            if (raw.isEmpty()) {
                return null;
            }
            try {
                int n = Integer.parseInt(raw);
                if (zeroPad) {
                    return (n >= 0 && n <= 59) ? n : null;
                }
                return (n >= 1 && n <= 12) ? n : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        void setOnChanged(Runnable r) {
            this.onChanged = r == null ? () -> {} : r;
        }

        String getValue() {
            return values.get(index);
        }

        void setValue(String value) {
            int i = values.indexOf(value);
            if (i >= 0) {
                index = i;
                if (!editing) {
                    render();
                }
            }
        }

        private void step(int delta) {
            index = Math.floorMod(index + delta, values.size());
            render();
            onChanged.run();
        }

        private void render() {
            int size = values.size();
            prev.setText(values.get(Math.floorMod(index - 1, size)));
            current.setText(values.get(index));
            next.setText(values.get(Math.floorMod(index + 1, size)));
        }
    }
}
