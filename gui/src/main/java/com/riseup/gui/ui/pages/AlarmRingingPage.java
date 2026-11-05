package com.riseup.gui.ui.pages;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.audio.AlarmSoundPlayer;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AlarmRingingPage extends VBox {
    private final AlarmSoundPlayer soundPlayer = new AlarmSoundPlayer();
    private PauseTransition unlockTimer;
    private Timeline countdown;
    private int unlockSecondsLeft;

    public AlarmRingingPage(ApiClient api, MainShell shell, Integer alarmId) {
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Active",
                "Alarm ringing",
                "Solve the challenge to stop the sound. A new question unlocks after 10 seconds."));

        Label challenge = new Label(alarmId == null
                ? "No ringing alarm selected."
                : "Loading challenge…");
        challenge.getStyleClass().add("challenge-text");
        challenge.setWrapText(true);

        Label progress = UiKit.muted("");
        TextField answer = UiKit.textField("Type your answer here", "");
        Label photoLabel = UiKit.muted("No photo attached");
        Label status = UiKit.muted("");
        FlowPane choices = new FlowPane(8, 8);
        choices.setMaxWidth(Double.MAX_VALUE);

        Button reload = new Button("New question");
        reload.getStyleClass().add("secondary-btn");
        Button verify = new Button("Verify & dismiss");
        verify.getStyleClass().add("primary-btn");
        Button photo = new Button("Attach photo");
        photo.getStyleClass().add("ghost-btn");
        Button snooze = new Button("Snooze");
        snooze.getStyleClass().add("danger-btn");
        Button back = new Button("Back to dashboard");
        back.getStyleClass().add("ghost-btn");

        final String[] imageBase64 = {null};
        final Integer[] idHolder = {alarmId};
        final String[] soundName = {"classic"};

        Runnable stopSound = soundPlayer::stop;
        back.setOnAction(e -> {
            stopTimers();
            stopSound.run();
            shell.navigate("Dashboard");
        });

        if (alarmId == null) {
            verify.setDisable(true);
            snooze.setDisable(true);
            reload.setDisable(true);
            photo.setDisable(true);
        }

        Runnable lockNewQuestion = () -> {
            stopTimers();
            unlockSecondsLeft = 10;
            reload.setDisable(true);
            reload.setText("New question (10s)");
            countdown = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                unlockSecondsLeft--;
                if (unlockSecondsLeft > 0) {
                    reload.setText("New question (" + unlockSecondsLeft + "s)");
                }
            }));
            countdown.setCycleCount(10);
            countdown.play();
            unlockTimer = new PauseTransition(Duration.seconds(10));
            unlockTimer.setOnFinished(ev -> {
                reload.setDisable(false);
                reload.setText("New question");
            });
            unlockTimer.play();
        };

        Consumer<JsonNode> renderChallenge = ch -> {
            String type = ch.path("type").asText("challenge");
            String q = ch.path("question").asText(ch.path("target").asText("Complete the task"));
            challenge.setText(q);
            int idx = ch.path("series_index").asInt(0);
            int total = ch.path("series_total").asInt(0);
            String diff = ch.path("difficulty").asText("");
            if (idx > 0 && total > 0) {
                progress.setText("Progress " + idx + " of " + total
                        + (diff.isBlank() ? "" : " · " + diff));
            } else if (!diff.isBlank()) {
                progress.setText("Difficulty: " + diff);
            } else {
                progress.setText(type);
            }
            choices.getChildren().clear();
            if (ch.path("choices").isArray() && ch.path("choices").size() > 0) {
                answer.setPromptText("Tap a choice or type it");
                for (JsonNode choice : ch.path("choices")) {
                    Button b = new Button(choice.asText());
                    b.getStyleClass().add("secondary-btn");
                    b.setOnAction(ev -> {
                        answer.setText(choice.asText());
                        verify.fire();
                    });
                    choices.getChildren().add(b);
                }
            } else {
                answer.setPromptText("Type your answer here");
            }
            answer.clear();
            Platform.runLater(answer::requestFocus);
            lockNewQuestion.run();
        };

        Runnable load = () -> {
            if (idHolder[0] == null) {
                status.setText("No alarm id");
                return;
            }
            try {
                JsonNode c = api.get("/api/alarms/" + idHolder[0] + "/challenge");
                soundName[0] = c.path("sound").asText("classic");
                if (!soundPlayer.isPlaying()) {
                    soundPlayer.play(soundName[0]);
                }
                renderChallenge.accept(c.path("challenge"));
                status.setText("Status: " + c.path("status").asText());
            } catch (Exception ex) {
                status.setText(UiKit.userError(ex));
                shell.toastError(UiKit.userError(ex));
            }
        };

        reload.setOnAction(e -> {
            if (reload.isDisable()) {
                return;
            }
            UiKit.runBusy(reload, "Loading…", load);
        });
        photo.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose verification photo");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                    new FileChooser.ExtensionFilter("All files", "*.*")
            );
            var file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (file != null) {
                try {
                    imageBase64[0] = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                    photoLabel.setText("Photo ready: " + file.getName());
                    shell.toastOk("Photo attached.");
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            }
        });

        Runnable doVerify = () -> {
            if (idHolder[0] == null) {
                return;
            }
            if (answer.getText().isBlank() && imageBase64[0] == null) {
                shell.toastError("Enter an answer (or attach a photo) before verifying.");
                answer.requestFocus();
                return;
            }
            UiKit.runBusy(verify, "Checking…", () -> {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (!answer.getText().isBlank()) {
                        body.put("answer", answer.getText().trim());
                    }
                    body.put("completed", true);
                    if (imageBase64[0] != null) {
                        body.put("image_base64", imageBase64[0]);
                    }
                    JsonNode result = api.post("/api/alarms/" + idHolder[0] + "/verify", body);
                    String msg = result.path("message").asText("Checked.");
                    status.setText(msg);
                    boolean ok = result.path("success").asBoolean(false);
                    boolean finished = result.path("finished").asBoolean(ok);
                    if (ok && finished) {
                        stopTimers();
                        stopSound.run();
                        shell.toastOk(msg.isBlank() ? "Alarm dismissed." : msg);
                        shell.navigate("Dashboard");
                    } else if (ok) {
                        shell.toastOk(msg);
                        if (result.has("challenge")) {
                            renderChallenge.accept(result.path("challenge"));
                        } else {
                            load.run();
                        }
                    } else {
                        shell.toastError(msg.isBlank() ? "Not quite — try again." : msg);
                        if (result.has("challenge")) {
                            renderChallenge.accept(result.path("challenge"));
                        } else {
                            load.run();
                        }
                    }
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            });
        };
        verify.setOnAction(e -> doVerify.run());
        UiKit.onEnter(answer, doVerify);

        snooze.setOnAction(e -> UiKit.runBusy(snooze, "…", () -> {
            try {
                api.post("/api/alarms/" + idHolder[0] + "/snooze", Map.of());
                stopTimers();
                stopSound.run();
                shell.toastOk("Snoozed — it’ll ring again shortly.");
                shell.navigate("Dashboard");
            } catch (Exception ex) {
                shell.toastError(UiKit.userError(ex));
            }
        }));

        VBox panel = new VBox(14,
                challenge,
                progress,
                choices,
                UiKit.labeled("Your answer", answer),
                photoLabel,
                Panels.actionRow(verify, reload, photo, snooze, back),
                status);
        panel.getStyleClass().add("ringing-panel");

        ScaleTransition pulse = new ScaleTransition(Duration.millis(900), panel);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.012);
        pulse.setToY(1.012);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();

        getChildren().add(panel);

        sceneProperty().addListener((obs, oldS, newS) -> {
            if (newS == null) {
                stopTimers();
                stopSound.run();
            }
        });

        if (alarmId != null) {
            load.run();
            Platform.runLater(() -> soundPlayer.play(soundName[0]));
        }
    }

    private void stopTimers() {
        if (unlockTimer != null) {
            unlockTimer.stop();
        }
        if (countdown != null) {
            countdown.stop();
        }
    }
}
