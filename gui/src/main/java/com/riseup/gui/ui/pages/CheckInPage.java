package com.riseup.gui.ui.pages;

import com.riseup.gui.api.ApiClient;
import com.riseup.gui.ui.MainShell;
import com.riseup.gui.ui.components.Panels;
import com.riseup.gui.ui.components.UiKit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Post-wake check-in: answer a short challenge within a time limit.
 * If the timer expires, the alarm is triggered again.
 */
public class CheckInPage extends VBox {
    private Timeline countdown;
    private int secondsLeft = 90;

    public CheckInPage(ApiClient api, MainShell shell, Integer alarmId) {
        getStyleClass().add("page");
        setSpacing(16);

        getChildren().add(Panels.pageHeader(
                "Still awake?",
                "Check-in",
                "Prove you’re up. If time runs out, the alarm rings again."));

        Challenge ch = Challenge.random();

        Label timer = new Label("◷  90s left");
        timer.getStyleClass().add("page-kicker");
        Label question = new Label(ch.prompt());
        question.getStyleClass().add("challenge-text");
        question.setWrapText(true);
        TextField answer = UiKit.textField("Type your answer…", "");
        Label status = UiKit.muted("You have 90 seconds.");

        Button confirm = new Button("Confirm I'm up");
        confirm.getStyleClass().add("primary-btn");
        Button back = new Button("Back");
        back.getStyleClass().add("ghost-btn");

        Runnable failAndRering = () -> {
            if (countdown != null) {
                countdown.stop();
            }
            status.setText("Time’s up. Ringing the alarm again.");
            shell.toastError("Check-in timed out. Alarm is ringing again.");
            if (alarmId != null) {
                try {
                    api.post("/api/alarms/" + alarmId + "/trigger", Map.of());
                    shell.openRinging(alarmId);
                    return;
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            }
            shell.navigate("Dashboard");
        };

        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            timer.setText("◷  " + secondsLeft + "s left");
            if (secondsLeft <= 0) {
                failAndRering.run();
            }
        }));
        countdown.setCycleCount(90);
        countdown.play();

        confirm.setOnAction(e -> {
            String given = answer.getText() == null ? "" : answer.getText().trim();
            if (given.isBlank()) {
                shell.toastError("Type the answer first.");
                answer.requestFocus();
                return;
            }
            if (!ch.matches(given)) {
                shell.toastError("Not quite. Try again before time runs out.");
                answer.clear();
                answer.requestFocus();
                return;
            }
            if (countdown != null) {
                countdown.stop();
            }
            UiKit.runBusy(confirm, "Confirming…", () -> {
                try {
                    var res = api.post("/api/checkins/confirm", Map.of());
                    shell.toastOk(res.path("message").asText("You’re checked in. Have a great day!"));
                    shell.navigate("Dashboard");
                } catch (Exception ex) {
                    shell.toastError(UiKit.userError(ex));
                }
            });
        });
        UiKit.onEnter(answer, confirm::fire);
        back.setOnAction(e -> {
            if (countdown != null) {
                countdown.stop();
            }
            shell.navigate("Dashboard");
        });

        VBox panel = new VBox(14, timer, question,
                UiKit.labeled("Your answer", answer),
                Panels.actionRow(confirm, back),
                status);
        panel.getStyleClass().add("ringing-panel");
        getChildren().add(panel);

        sceneProperty().addListener((o, a, n) -> {
            if (n == null && countdown != null) {
                countdown.stop();
            }
        });
        Platform.runLater(answer::requestFocus);
    }

    private record Challenge(String prompt, String answer) {
        boolean matches(String given) {
            return answer.equalsIgnoreCase(given.trim())
                    || answer.replace(" ", "").equalsIgnoreCase(given.replace(" ", ""));
        }

        static Challenge random() {
            int roll = ThreadLocalRandom.current().nextInt(6);
            return switch (roll) {
                case 0 -> {
                    int a = ThreadLocalRandom.current().nextInt(8) + 2;
                    int b = ThreadLocalRandom.current().nextInt(8) + 2;
                    yield new Challenge("Quick math: what is " + a + " + " + b + "?", String.valueOf(a + b));
                }
                case 1 -> {
                    int a = ThreadLocalRandom.current().nextInt(9) + 6;
                    int b = ThreadLocalRandom.current().nextInt(5) + 1;
                    yield new Challenge("Quick math: what is " + a + " − " + b + "?", String.valueOf(a - b));
                }
                case 2 -> new Challenge(
                        "Type the word AWAKE in capital letters.",
                        "AWAKE");
                case 3 -> new Challenge(
                        "Type today’s weekday (e.g. Monday).",
                        java.time.LocalDate.now().getDayOfWeek()
                                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH));
                case 4 -> new Challenge(
                        "How many letters are in the word RISEUP?",
                        "6");
                default -> new Challenge(
                        "Type YES to confirm you’re up.",
                        "YES");
            };
        }
    }
}
