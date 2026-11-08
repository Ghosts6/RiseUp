package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.DateTimePickers;
import com.riseup.gui.ui.components.Icons;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.SoundPicker;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TasksPage extends VBox {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApiClient api;
    private final MainShell shell;
    private final VBox listBox = new VBox(0);
    private final Label listHint = UiKit.muted("Loading…");
    private String statusFilter = "active";
    private String kindFilter = "all";
    private String composeKind = "task";
    private Integer editingId = null;
    private LocalDate selectedDay = LocalDate.now();
    private LocalTime selectedTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
    private final List<JsonNode> cache = new ArrayList<>();
    private TextField titleField;
    private ComboBox<String> priority;
    private SoundPicker soundPicker;
    private Button addBtn;
    private Button kindTask;
    private Button kindEvent;
    private Runnable refreshWhen = () -> {};

    public TasksPage(ApiClient api, MainShell shell) {
        this.api = api;
        this.shell = shell;
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Planner",
                "Tasks & Events",
                "Pick Task or Event, choose day & time from popups, and set a sound."));

        kindTask = UiKit.filterChip("Task");
        kindEvent = UiKit.filterChip("Event");
        UiKit.selectFilter(kindTask, kindTask, kindEvent);

        TextField nl = UiKit.textField("e.g. Remind me to call Mom tomorrow at 10am", "");
        Button parse = new Button("Fill from text");
        parse.getStyleClass().add("secondary-btn");

        titleField = UiKit.textField("Title", "");

        Button dayBtn = whenField(DateTimePickers.formatDay(selectedDay));
        Button timeBtn = whenField(DateTimePickers.formatTime(selectedTime));
        HBox.setHgrow(dayBtn, Priority.ALWAYS);
        HBox.setHgrow(timeBtn, Priority.ALWAYS);
        Icons.setGraphic(dayBtn, Icons.CALENDAR, 16, Icons.LIGHT_ACCENT);
        Icons.setGraphic(timeBtn, Icons.CLOCK, 16, Icons.LIGHT_ACCENT);

        refreshWhen = () -> {
            dayBtn.setText(DateTimePickers.formatDay(selectedDay));
            timeBtn.setText(DateTimePickers.formatTime(selectedTime));
            boolean dark = getScene() != null && getScene().getRoot().getStyleClass().contains("dark");
            Icons.setGraphic(dayBtn, Icons.CALENDAR, 16, Icons.accentColor(dark));
            Icons.setGraphic(timeBtn, Icons.CLOCK, 16, Icons.accentColor(dark));
        };

        dayBtn.setOnAction(e -> DateTimePickers.pickDate(
                getScene() == null ? null : getScene().getWindow(), selectedDay)
                .ifPresent(d -> {
                    selectedDay = d;
                    refreshWhen.run();
                }));
        timeBtn.setOnAction(e -> DateTimePickers.pickTime(
                getScene() == null ? null : getScene().getWindow(), selectedTime)
                .ifPresent(t -> {
                    selectedTime = t;
                    refreshWhen.run();
                }));

        priority = new ComboBox<>();
        priority.getItems().addAll("low", "medium", "high");
        priority.getSelectionModel().select("medium");
        priority.setMaxWidth(Double.MAX_VALUE);
        UiKit.openComboDown(priority);

        soundPicker = new SoundPicker();

        addBtn = new Button("Add");
        addBtn.getStyleClass().add("primary-btn");
        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");

        Runnable syncComposeKind = () -> {
            if (editingId != null) {
                addBtn.setText("Save changes");
            } else if ("event".equals(composeKind)) {
                titleField.setPromptText("What’s the event?");
                addBtn.setText("Add event");
            } else {
                titleField.setPromptText("What do you need to do?");
                addBtn.setText("Add task");
            }
        };
        kindTask.setOnAction(e -> {
            composeKind = "task";
            UiKit.selectFilter(kindTask, kindTask, kindEvent);
            syncComposeKind.run();
        });
        kindEvent.setOnAction(e -> {
            composeKind = "event";
            UiKit.selectFilter(kindEvent, kindTask, kindEvent);
            syncComposeKind.run();
        });
        syncComposeKind.run();

        parse.setOnAction(e -> UiKit.runBusy(parse, "Parsing…", () -> {
            if (nl.getText().isBlank()) {
                shell.toastError("Type a reminder sentence first.");
                return;
            }
            try {
                JsonNode parsed = api.post("/api/reminders/parse", Map.of("text", nl.getText().trim()));
                JsonNode draft = parsed.path("draft");
                if (!draft.isMissingNode()) {
                    titleField.setText(draft.path("title").asText(titleField.getText()));
                    String dueVal = draft.path("due").asText(draft.path("due_at").asText(""));
                    if (!dueVal.isBlank()) {
                        try {
                            var dt = UiKit.parseFlexibleDateTime(dueVal.replace(' ', 'T'));
                            selectedDay = dt.toLocalDate();
                            selectedTime = dt.toLocalTime().withSecond(0).withNano(0);
                            refreshWhen.run();
                        } catch (Exception ignored) {
                        }
                    }
                    shell.toastOk("Form filled. Review day, time, and sound.");
                } else {
                    shell.toastError("Could not understand that.");
                }
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }));

        Runnable doAdd = () -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            if (title.isBlank()) {
                shell.toastError("Add a short title.");
                titleField.requestFocus();
                return;
            }
            if (selectedDay == null || selectedTime == null) {
                shell.toastError("Pick a day and a time.");
                return;
            }
            UiKit.runBusy(addBtn, "Saving…", () -> {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("title", title);
                    body.put("due", DateTimePickers.toDueIso(selectedDay, selectedTime));
                    body.put("priority", priority.getValue());
                    body.put("category", composeKind);
                    body.put("repeat_pattern", "none");
                    body.put("notification_type", "push");
                    body.put("notes", JSON.writeValueAsString(Map.of("sound", soundPicker.getSoundKey())));
                    if (editingId != null) {
                        api.patch("/api/reminders/" + editingId + "?scope=this", body);
                        shell.toastOk("Updated.");
                        editingId = null;
                    } else {
                        api.post("/api/reminders", body);
                        shell.toastOk(("event".equals(composeKind) ? "Event" : "Task") + " added.");
                    }
                    titleField.clear();
                    nl.clear();
                    syncComposeKind.run();
                    reload();
                    titleField.requestFocus();
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            });
        };
        addBtn.setOnAction(e -> doAdd.run());
        UiKit.onEnter(titleField, doAdd);

        clear.setOnAction(e -> {
            nl.clear();
            titleField.clear();
            editingId = null;
            selectedDay = LocalDate.now();
            selectedTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
            refreshWhen.run();
            soundPicker.setSoundKey("classic");
            priority.getSelectionModel().select("medium");
            syncComposeKind.run();
        });

        VBox dayField = UiKit.labeled("Day", dayBtn);
        VBox timeField = UiKit.labeled("Time", timeBtn);
        HBox.setHgrow(dayField, Priority.ALWAYS);
        HBox.setHgrow(timeField, Priority.ALWAYS);
        HBox whenRow = new HBox(12, dayField, timeField);
        whenRow.setMaxWidth(Double.MAX_VALUE);
        whenRow.getStyleClass().add("when-row");

        VBox composer = Panels.card("Quick add",
                new HBox(8, kindTask, kindEvent),
                UiKit.labeled("Say it in plain English (optional)", nl),
                Panels.actionRow(parse),
                UiKit.labeled("Title", titleField),
                whenRow,
                UiKit.labeled("Sound", soundPicker),
                UiKit.labeled("Priority", priority),
                Panels.actionRow(addBtn, clear));

        Button fActive = UiKit.filterChip("Active");
        Button fDone = UiKit.filterChip("Done");
        Button fAllStatus = UiKit.filterChip("All status");
        UiKit.selectFilter(fActive, fActive, fDone, fAllStatus);
        Button fKindAll = UiKit.filterChip("All kinds");
        Button fKindTask = UiKit.filterChip("Tasks");
        Button fKindEvent = UiKit.filterChip("Events");
        UiKit.selectFilter(fKindAll, fKindAll, fKindTask, fKindEvent);

        fActive.setOnAction(e -> { statusFilter = "active"; UiKit.selectFilter(fActive, fActive, fDone, fAllStatus); renderList(); });
        fDone.setOnAction(e -> { statusFilter = "done"; UiKit.selectFilter(fDone, fActive, fDone, fAllStatus); renderList(); });
        fAllStatus.setOnAction(e -> { statusFilter = "all"; UiKit.selectFilter(fAllStatus, fActive, fDone, fAllStatus); renderList(); });
        fKindAll.setOnAction(e -> { kindFilter = "all"; UiKit.selectFilter(fKindAll, fKindAll, fKindTask, fKindEvent); renderList(); });
        fKindTask.setOnAction(e -> { kindFilter = "task"; UiKit.selectFilter(fKindTask, fKindAll, fKindTask, fKindEvent); renderList(); });
        fKindEvent.setOnAction(e -> { kindFilter = "event"; UiKit.selectFilter(fKindEvent, fKindAll, fKindTask, fKindEvent); renderList(); });

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("ghost-btn");
        refresh.setOnAction(e -> reload());

        HBox filters = new HBox(8, fActive, fDone, fAllStatus, fKindAll, fKindTask, fKindEvent);
        filters.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(composer, Panels.card("Your list",
                UiKit.grow(filters, refresh),
                listHint,
                listBox));
        reload();
        javafx.application.Platform.runLater(titleField::requestFocus);
    }

    private void reload() {
        listHint.setText("Loading…");
        try {
            cache.clear();
            for (JsonNode r : api.get("/api/reminders")) {
                cache.add(r);
            }
            renderList();
        } catch (Exception ex) {
            listHint.setText(UiKit.userError(ex));
            shell.toastError(UiKit.userError(ex));
        }
    }

    private void renderList() {
        listBox.getChildren().clear();
        List<JsonNode> shown = new ArrayList<>();
        for (JsonNode r : cache) {
            String status = r.path("status").asText("").toLowerCase(Locale.ROOT);
            boolean done = status.contains("complete") || status.contains("done") || status.contains("cancelled");
            if ("active".equals(statusFilter) && done) continue;
            if ("done".equals(statusFilter) && !done) continue;
            String kind = normalizeKind(r.path("category").asText("task"));
            if (!"all".equals(kindFilter) && !kindFilter.equals(kind)) continue;
            shown.add(r);
        }
        if (shown.isEmpty()) {
            listHint.setText("");
            listBox.getChildren().add(UiKit.emptyState("Nothing here", "Add a task or event above."));
            return;
        }
        listHint.setText(shown.size() + (shown.size() == 1 ? " item" : " items"));
        shown.forEach(r -> listBox.getChildren().add(taskRow(r)));
    }

    private HBox taskRow(JsonNode r) {
        int id = r.path("id").asInt();
        String title = r.path("title").asText("Untitled");
        String dueLocal = r.path("due_local").asText(r.path("due_at").asText(""));
        String status = r.path("status").asText("pending");
        String priority = r.path("priority").asText("medium");
        String kind = normalizeKind(r.path("category").asText("task"));
        String sound = extractSound(r.path("notes").asText(""));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("item-title");
        titleLbl.setWrapText(true);
        Label meta = new Label(prettyKind(kind) + " · " + UiKit.friendlyDue(dueLocal)
                + " · " + priority + (sound.isBlank() ? "" : " · ◈ " + sound));
        meta.getStyleClass().add("muted");
        VBox text = new VBox(3, titleLbl, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button edit = new Button("Edit");
        edit.getStyleClass().add("ghost-btn");
        edit.setOnAction(e -> beginEdit(r));
        Button done = new Button("Done");
        done.getStyleClass().add("primary-btn");
        done.setOnAction(e -> UiKit.runBusy(done, "…", () -> {
            try {
                api.post("/api/reminders/" + id + "/complete", Map.of());
                shell.toastOk("Marked done.");
                reload();
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }));
        Button snooze = new Button("Snooze 10m");
        snooze.getStyleClass().add("secondary-btn");
        snooze.setOnAction(e -> UiKit.runBusy(snooze, "…", () -> {
            try {
                api.post("/api/reminders/" + id + "/snooze", Map.of("minutes", 10));
                shell.toastOk("Snoozed.");
                reload();
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-btn");
        delete.setOnAction(e -> {
            if (!UiKit.confirm(this, "Delete", "Delete “" + title + "”?")) return;
            try {
                api.delete("/api/reminders/" + id);
                shell.toastOk("Deleted.");
                reload();
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        });
        boolean completed = status.toLowerCase(Locale.ROOT).contains("complete")
                || status.toLowerCase(Locale.ROOT).contains("done");
        done.setDisable(completed);
        snooze.setDisable(completed);
        edit.setDisable(completed);

        HBox row = new HBox(14, UiKit.statusBadge(prettyKind(kind)), text,
                new HBox(8, edit, done, snooze, delete));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private void beginEdit(JsonNode r) {
        editingId = r.path("id").asInt();
        titleField.setText(r.path("title").asText(""));
        composeKind = normalizeKind(r.path("category").asText("task"));
        if ("event".equals(composeKind)) {
            UiKit.selectFilter(kindEvent, kindTask, kindEvent);
        } else {
            UiKit.selectFilter(kindTask, kindTask, kindEvent);
        }
        priority.getSelectionModel().select(r.path("priority").asText("medium"));
        String sound = extractSound(r.path("notes").asText(""));
        if (!sound.isBlank()) {
            soundPicker.setSoundKey(sound);
        }
        String dueVal = r.path("due_local").asText(r.path("due_at").asText(""));
        if (!dueVal.isBlank()) {
            try {
                var dt = UiKit.parseFlexibleDateTime(dueVal.replace(' ', 'T'));
                selectedDay = dt.toLocalDate();
                selectedTime = dt.toLocalTime().withSecond(0).withNano(0);
                refreshWhen.run();
            } catch (Exception ignored) {
            }
        }
        addBtn.setText("Save changes");
        titleField.requestFocus();
        shell.toastOk("Editing in the form above.");
    }

    private static String extractSound(String notes) {
        if (notes == null || !notes.trim().startsWith("{")) {
            return "";
        }
        try {
            return JSON.readTree(notes).path("sound").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    static String normalizeKind(String category) {
        if (category != null && category.toLowerCase(Locale.ROOT).contains("event")) {
            return "event";
        }
        return "task";
    }

    static String prettyKind(String kind) {
        return "event".equals(kind) ? "Event" : "Task";
    }

    private static Button whenField(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("secondary-btn", "when-field");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }
}
