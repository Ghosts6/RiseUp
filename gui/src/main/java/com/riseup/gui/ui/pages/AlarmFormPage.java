package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.ClockTimePicker;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.SoundPicker;
import com.riseup.gui.ui.components.UiKit;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AlarmFormPage extends VBox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] DAY_KEYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    private static final String[] DAY_LABELS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] DAY_FULL = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private final ClockTimePicker picker = new ClockTimePicker();
    private final Label timeHero = new Label();
    private final Set<String> selectedDays = new LinkedHashSet<>();
    private final List<ToggleButton> dayToggles = new ArrayList<>();
    private final SoundPicker soundPicker = new SoundPicker();
    private boolean syncingDays;
    private boolean snoozeOn = true;

    public AlarmFormPage(ApiClient api, MainShell shell, Integer id) {
        getStyleClass().add("page");
        setSpacing(16);
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        getChildren().add(Panels.pageHeader(
                "Alarms",
                id == null ? "New alarm" : "Edit alarm",
                "Pick a time, then choose sound, repeat, and how hard the wake-up challenge should be."));

        TextField label = UiKit.textField("Alarm name", "Wake Up");
        TextField custom = UiKit.textField("Write your custom challenge…", "");

        timeHero.getStyleClass().add("alarm-time-hero");
        timeHero.setText(picker.getDisplayText());
        picker.setOnChange(() -> timeHero.setText(picker.getDisplayText()));
        picker.setMaxWidth(Double.MAX_VALUE);

        VBox timeCard = new VBox(10);
        timeCard.getStyleClass().add("clock-card");
        timeCard.setAlignment(Pos.CENTER);
        timeCard.setFillWidth(true);
        timeCard.setMaxWidth(Double.MAX_VALUE);
        timeCard.getChildren().addAll(timeHero, picker,
                UiKit.muted("Scroll, tap ▴ ▾, or click the selected number to type"));

        // Repeat
        ComboBox<String> repeatBox = new ComboBox<>();
        repeatBox.getItems().addAll(
                "Never (once)",
                "Every day",
                "Weekdays",
                "Weekends",
                "Custom days");
        repeatBox.getSelectionModel().select(0);
        repeatBox.setMaxWidth(Double.MAX_VALUE);
        UiKit.openComboDown(repeatBox);

        VBox daysBox = new VBox(8);
        daysBox.setVisible(false);
        daysBox.setManaged(false);
        HBox dayRow = new HBox(6);
        dayRow.getStyleClass().add("day-row");
        dayRow.setMaxWidth(Double.MAX_VALUE);
        dayRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < DAY_KEYS.length; i++) {
            final String key = DAY_KEYS[i];
            ToggleButton day = new ToggleButton(DAY_LABELS[i]);
            day.getStyleClass().add("day-toggle");
            day.setTooltip(new javafx.scene.control.Tooltip(DAY_FULL[i]));
            day.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(day, Priority.ALWAYS);
            day.selectedProperty().addListener((obs, was, on) -> {
                if (syncingDays) {
                    return;
                }
                if (on) {
                    selectedDays.add(key);
                } else {
                    selectedDays.remove(key);
                }
            });
            dayToggles.add(day);
            dayRow.getChildren().add(day);
        }
        daysBox.getChildren().addAll(UiKit.muted("Which days?"), dayRow);
        repeatBox.valueProperty().addListener((o, a, b) -> {
            boolean customDays = "Custom days".equals(b);
            daysBox.setVisible(customDays);
            daysBox.setManaged(customDays);
            if ("Weekdays".equals(b)) {
                selectDays("mon", "tue", "wed", "thu", "fri");
            } else if ("Weekends".equals(b)) {
                selectDays("sat", "sun");
            } else if ("Every day".equals(b)) {
                selectDays(DAY_KEYS);
            } else if ("Never (once)".equals(b)) {
                clearDays();
            }
        });

        soundPicker.setMaxWidth(Double.MAX_VALUE);

        // Snooze
        ComboBox<String> snoozeBox = new ComboBox<>();
        snoozeBox.getItems().addAll("Off", "On · 5 min", "On · 9 min", "On · 10 min", "On · 15 min", "On · 30 min");
        snoozeBox.getSelectionModel().select("On · 9 min");
        snoozeBox.setMaxWidth(Double.MAX_VALUE);
        snoozeBox.valueProperty().addListener((o, a, b) -> snoozeOn = b != null && b.startsWith("On"));
        UiKit.openComboDown(snoozeBox);

        // Challenge
        ComboBox<String> challengeBox = new ComboBox<>();
        challengeBox.getItems().addAll("Math", "Trivia", "Photo object", "Custom");
        challengeBox.getSelectionModel().select("Math");
        challengeBox.setMaxWidth(Double.MAX_VALUE);
        UiKit.openComboDown(challengeBox);

        ComboBox<String> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll("Easy", "Medium", "Hard");
        difficultyBox.getSelectionModel().select("Medium");
        difficultyBox.setMaxWidth(Double.MAX_VALUE);
        UiKit.openComboDown(difficultyBox);

        ComboBox<String> questionsBox = new ComboBox<>();
        questionsBox.getItems().addAll("1 question", "2 questions", "3 questions", "4 questions", "5 questions");
        questionsBox.getSelectionModel().select("1 question");
        questionsBox.setMaxWidth(Double.MAX_VALUE);
        UiKit.openComboDown(questionsBox);

        VBox customBox = UiKit.labeled("Custom challenge text", custom);
        VBox difficultyRow = UiKit.labeled("Difficulty", difficultyBox);
        VBox questionsRow = UiKit.labeled("How many questions", questionsBox);

        Runnable syncChallengeUi = () -> {
            String c = challengeBox.getValue();
            boolean isCustom = "Custom".equals(c);
            boolean isPhoto = "Photo object".equals(c);
            customBox.setVisible(isCustom);
            customBox.setManaged(isCustom);
            difficultyRow.setVisible(!isCustom && !isPhoto);
            difficultyRow.setManaged(!isCustom && !isPhoto);
            questionsRow.setVisible(!isCustom && !isPhoto);
            questionsRow.setManaged(!isCustom && !isPhoto);
        };
        challengeBox.valueProperty().addListener((o, a, b) -> syncChallengeUi.run());
        syncChallengeUi.run();

        VBox settings = new VBox(0);
        settings.getStyleClass().add("settings-group");
        settings.setMaxWidth(Double.MAX_VALUE);
        settings.getChildren().addAll(
                settingsField("Label", label),
                settingsField("Repeat", repeatBox),
                wrapSetting(daysBox),
                settingsField("Sound", soundPicker),
                settingsField("Snooze", snoozeBox),
                settingsField("Challenge", challengeBox),
                wrapSetting(difficultyRow),
                wrapSetting(questionsRow),
                wrapSetting(customBox));

        if (id != null) {
            try {
                var a = api.get("/api/alarms/" + id);
                picker.setTime24(a.path("time").asText("07:30"));
                timeHero.setText(picker.getDisplayText());
                label.setText(a.path("label").asText("Wake Up"));
                soundPicker.setSoundKey(a.path("sound").asText("classic"));
                snoozeOn = a.path("snooze_enabled").asBoolean(true);
                snoozeBox.getSelectionModel().select(snoozeOn ? "On · 9 min" : "Off");
                applyRepeatPattern(a.path("repeat_pattern").asText("once"), repeatBox, daysBox);
                String type = a.path("verification_task_type").asText("math");
                challengeBox.getSelectionModel().select(prettyChallenge(type));
                applyChallengeConfig(a.path("custom_task_text").asText(""), custom, difficultyBox, questionsBox);
                syncChallengeUi.run();
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }

        Button save = new Button(id == null ? "Save Alarm" : "Save Changes");
        save.getStyleClass().add("primary-btn");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-btn");
        Button delete = new Button("Delete Alarm");
        delete.getStyleClass().add("danger-btn");
        delete.setVisible(id != null);
        delete.setManaged(id != null);

        Runnable doSave = () -> {
            String lbl = label.getText() == null ? "" : label.getText().trim();
            if (lbl.isBlank()) {
                shell.toastError("Give this alarm a name.");
                label.requestFocus();
                return;
            }
            if ("Custom days".equals(repeatBox.getValue()) && selectedDays.isEmpty()) {
                shell.toastError("Pick at least one day to repeat on.");
                return;
            }
            if ("Custom".equals(challengeBox.getValue())
                    && (custom.getText() == null || custom.getText().isBlank())) {
                shell.toastError("Custom challenge needs text.");
                custom.requestFocus();
                return;
            }
            UiKit.runBusy(save, "Saving…", () -> {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("time", picker.getTime24());
                    body.put("label", lbl);
                    body.put("verification_task_type", challengeKey(challengeBox.getValue()));
                    body.put("custom_task_text", buildChallengeConfig(
                            challengeBox.getValue(), difficultyBox.getValue(),
                            questionsBox.getValue(), custom.getText()));
                    body.put("snooze_enabled", snoozeOn);
                    body.put("sound", soundPicker.getSoundKey());
                    body.put("vibration", "medium");
                    body.put("repeat_pattern", buildRepeatPattern(repeatBox.getValue()));
                    body.put("priority", 1);
                    body.put("is_active", true);
                    if (id == null) {
                        api.post("/api/alarms", body);
                        shell.toastOk("Alarm set for " + picker.getDisplayText());
                    } else {
                        api.patch("/api/alarms/" + id, body);
                        shell.toastOk("Alarm updated · " + picker.getDisplayText());
                    }
                    shell.navigate("Dashboard");
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            });
        };
        save.setOnAction(e -> doSave.run());
        cancel.setOnAction(e -> shell.navigate("Dashboard"));
        delete.setOnAction(e -> {
            if (!UiKit.confirm(this, "Delete Alarm", "Delete this alarm?")) {
                return;
            }
            try {
                api.delete("/api/alarms/" + id);
                shell.toastOk("Alarm deleted.");
                shell.navigate("Dashboard");
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        });

        getChildren().addAll(timeCard, settings, Panels.actionRow(save, cancel), delete);
        sceneProperty().addListener((obs, o, n) -> {
            if (n == null) {
                soundPicker.stopPreview();
            }
        });
        javafx.application.Platform.runLater(label::requestFocus);
    }

    private void applyChallengeConfig(
            String raw, TextField custom, ComboBox<String> difficulty, ComboBox<String> questions) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                var n = JSON.readTree(trimmed);
                String diff = n.path("difficulty").asText("medium");
                difficulty.getSelectionModel().select(prettyDiff(diff));
                int q = n.path("questions").asInt(1);
                questions.getSelectionModel().select(Math.max(1, Math.min(5, q)) + " question"
                        + (q == 1 ? "" : "s"));
                // fix plural mismatch for select
                for (String item : questions.getItems()) {
                    if (item.startsWith(String.valueOf(Math.max(1, Math.min(5, q))))) {
                        questions.getSelectionModel().select(item);
                        break;
                    }
                }
                custom.setText(n.path("text").asText(""));
                return;
            } catch (Exception ignored) {
            }
        }
        custom.setText(raw);
    }

    private static String buildChallengeConfig(String challengeLabel, String diffLabel, String qLabel, String customText) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("difficulty", diffKey(diffLabel));
        m.put("questions", parseQuestions(qLabel));
        if ("Custom".equals(challengeLabel) && customText != null && !customText.isBlank()) {
            m.put("text", customText.trim());
        }
        try {
            return JSON.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"difficulty\":\"medium\",\"questions\":1}";
        }
    }

    private String buildRepeatPattern(String label) {
        if (label == null) {
            return "once";
        }
        return switch (label) {
            case "Every day" -> "daily";
            case "Weekdays" -> "weekdays";
            case "Weekends" -> "weekends";
            case "Custom days" -> "custom:" + String.join(",", selectedDays);
            default -> "once";
        };
    }

    private void applyRepeatPattern(String pattern, ComboBox<String> repeatBox, VBox daysBox) {
        String p = pattern == null ? "once" : pattern.toLowerCase(Locale.ROOT);
        if (p.startsWith("custom:")) {
            repeatBox.getSelectionModel().select("Custom days");
            clearDays();
            for (String part : p.substring(7).split(",")) {
                if (!part.isBlank()) {
                    selectedDays.add(part.trim());
                }
            }
            syncDayToggles();
            daysBox.setVisible(true);
            daysBox.setManaged(true);
        } else if ("daily".equals(p)) {
            repeatBox.getSelectionModel().select("Every day");
        } else if ("weekdays".equals(p)) {
            repeatBox.getSelectionModel().select("Weekdays");
        } else if ("weekends".equals(p)) {
            repeatBox.getSelectionModel().select("Weekends");
        } else {
            repeatBox.getSelectionModel().select("Never (once)");
        }
    }

    private void selectDays(String... days) {
        selectedDays.clear();
        selectedDays.addAll(List.of(days));
        syncDayToggles();
    }

    private void clearDays() {
        selectedDays.clear();
        syncDayToggles();
    }

    private void syncDayToggles() {
        syncingDays = true;
        try {
            for (int i = 0; i < dayToggles.size(); i++) {
                dayToggles.get(i).setSelected(selectedDays.contains(DAY_KEYS[i]));
            }
        } finally {
            syncingDays = false;
        }
    }

    private static String challengeKey(String label) {
        return switch (label) {
            case "Trivia" -> "trivia";
            case "Photo object" -> "object_detection";
            case "Custom" -> "custom";
            default -> "math";
        };
    }

    private static String prettyChallenge(String key) {
        return switch (key) {
            case "trivia" -> "Trivia";
            case "object_detection" -> "Photo object";
            case "custom" -> "Custom";
            default -> "Math";
        };
    }

    private static String diffKey(String label) {
        if (label == null) {
            return "medium";
        }
        return label.toLowerCase(Locale.ROOT);
    }

    private static String prettyDiff(String d) {
        return switch (d == null ? "" : d.toLowerCase(Locale.ROOT)) {
            case "easy" -> "Easy";
            case "hard" -> "Hard";
            default -> "Medium";
        };
    }

    private static int parseQuestions(String label) {
        if (label == null) {
            return 1;
        }
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 1;
        }
    }

    private static HBox settingsField(String title, javafx.scene.Node field) {
        Label t = new Label(title);
        t.getStyleClass().add("settings-title");
        t.setMinWidth(100);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(field, Priority.ALWAYS);
        if (field instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        HBox row = new HBox(12, t, spacer, field);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private static VBox wrapSetting(javafx.scene.Node node) {
        VBox box = new VBox(node);
        box.getStyleClass().add("settings-row");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
