package com.riseup.gui.theme;

import javafx.animation.FadeTransition;
import javafx.scene.Scene;
import javafx.util.Duration;

public class ThemeManager {
    private boolean dark;

    public void apply(Scene scene, boolean darkMode) {
        this.dark = darkMode;
        var root = scene.getRoot();
        root.getStyleClass().removeAll("dark");
        if (darkMode) {
            root.getStyleClass().add("dark");
        }
    }

    public void toggle(Scene scene) {
        boolean next = !dark;
        this.dark = next;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(90), scene.getRoot());
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0.86);
        fadeOut.setOnFinished(e -> {
            scene.getRoot().getStyleClass().removeAll("dark");
            if (next) {
                scene.getRoot().getStyleClass().add("dark");
            }
            FadeTransition fadeIn = new FadeTransition(Duration.millis(140), scene.getRoot());
            fadeIn.setFromValue(0.86);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    public boolean isDark() {
        return dark;
    }
}
