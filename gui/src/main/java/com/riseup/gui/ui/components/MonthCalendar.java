package com.riseup.gui.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;

/** Inline month grid — no nested popup, so it never overflows the host sheet. */
public class MonthCalendar extends VBox {
    private YearMonth month;
    private LocalDate selected;
    private final Label monthLabel = new Label();
    private final GridPane grid = new GridPane();
    private Consumer<LocalDate> onChange = d -> {};

    public MonthCalendar(LocalDate initial) {
        getStyleClass().add("month-calendar");
        setSpacing(10);
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        this.selected = initial == null ? LocalDate.now() : initial;
        this.month = YearMonth.from(selected);

        Button prev = navBtn("‹");
        prev.setOnAction(e -> {
            month = month.minusMonths(1);
            render();
        });
        Button next = navBtn("›");
        next.setOnAction(e -> {
            month = month.plusMonths(1);
            render();
        });
        Button today = new Button("Today");
        today.getStyleClass().addAll("ghost-btn", "cal-today-btn");
        today.setOnAction(e -> {
            selected = LocalDate.now();
            month = YearMonth.from(selected);
            render();
            onChange.accept(selected);
        });

        monthLabel.getStyleClass().add("cal-month-label");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, prev, monthLabel, spacer, today, next);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("cal-header");

        GridPane weekdays = new GridPane();
        weekdays.getStyleClass().add("cal-weekdays");
        weekdays.setHgap(4);
        weekdays.setMaxWidth(Double.MAX_VALUE);
        String[] days = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (int i = 0; i < 7; i++) {
            Label d = new Label(days[i]);
            d.getStyleClass().add("cal-weekday");
            d.setMaxWidth(Double.MAX_VALUE);
            d.setAlignment(Pos.CENTER);
            weekdays.add(d, i, 0);
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            cc.setHgrow(Priority.ALWAYS);
            weekdays.getColumnConstraints().add(cc);
            grid.getColumnConstraints().add(copyCol(cc));
        }

        grid.getStyleClass().add("cal-grid");
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, weekdays, grid);
        render();
    }

    private static ColumnConstraints copyCol(ColumnConstraints src) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(src.getPercentWidth());
        cc.setHgrow(Priority.ALWAYS);
        return cc;
    }

    private static Button navBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("secondary-btn", "cal-nav-btn");
        b.setMinWidth(36);
        return b;
    }

    public void setOnChange(Consumer<LocalDate> onChange) {
        this.onChange = onChange == null ? d -> {} : onChange;
    }

    public LocalDate getSelected() {
        return selected;
    }

    public void setSelected(LocalDate date) {
        if (date == null) {
            return;
        }
        selected = date;
        month = YearMonth.from(date);
        render();
    }

    private void render() {
        monthLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + month.getYear());
        grid.getChildren().clear();

        LocalDate first = month.atDay(1);
        int offset = first.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue(); // 0=Mon
        LocalDate start = first.minusDays(offset);
        LocalDate today = LocalDate.now();

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                LocalDate day = start.plusDays(row * 7L + col);
                Button cell = new Button(String.valueOf(day.getDayOfMonth()));
                cell.getStyleClass().add("cal-day");
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setMinHeight(36);
                if (!YearMonth.from(day).equals(month)) {
                    cell.getStyleClass().add("cal-day-outside");
                }
                if (day.equals(today)) {
                    cell.getStyleClass().add("cal-day-today");
                }
                if (day.equals(selected)) {
                    cell.getStyleClass().add("cal-day-selected");
                }
                final LocalDate picked = day;
                cell.setOnAction(e -> {
                    selected = picked;
                    if (!YearMonth.from(picked).equals(month)) {
                        month = YearMonth.from(picked);
                    }
                    render();
                    onChange.accept(selected);
                });
                grid.add(cell, col, row);
            }
        }
    }
}
