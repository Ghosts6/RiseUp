package com.riseup.gui.ui.components;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads mono PNG icons from /icons/png and tints them for light/dark themes. */
public final class Icons {
    public static final String DASHBOARD = "dashboard";
    public static final String SPARKLES = "sparkles";
    public static final String ALARM = "alarm-clock";
    public static final String CHECK_SQUARE = "check-square";
    public static final String CHART = "chart-line";
    public static final String LIGHTBULB = "lightbulb";
    public static final String HISTORY = "history";
    public static final String SETTINGS = "settings";
    public static final String SUN = "sun";
    public static final String MOON = "moon";
    public static final String WIFI = "wifi";
    public static final String WIFI_OFF = "wifi-off";
    public static final String CLOCK = "clock";
    public static final String CALENDAR = "calendar";
    public static final String VOLUME = "volume";
    public static final String PLAY = "play";
    public static final String SNOOZE = "snooze";
    public static final String SHIELD = "shield-check";
    public static final String CIRCLE_CHECK = "circle-check";
    public static final String CHECK = "check";
    public static final String TRASH = "trash";
    public static final String SEND = "send";
    public static final String MESSAGE_PLUS = "message-plus";
    public static final String REFRESH = "refresh";
    public static final String ARROW_LEFT = "arrow-left";
    public static final String PENCIL = "pencil";
    public static final String ALERT = "alert";
    public static final String CIRCLE_X = "circle-x";

    /** Light-theme nav / body icon color (matches -ru-nav). */
    public static final Color LIGHT_NAV = Color.web("#243247");
    /** Dark-theme nav / body icon color. */
    public static final Color DARK_NAV = Color.web("#d7e0ea");
    /** Light accent. */
    public static final Color LIGHT_ACCENT = Color.web("#e85d04");
    /** Dark accent. */
    public static final Color DARK_ACCENT = Color.web("#ff8a4c");
    public static final Color WHITE = Color.WHITE;
    public static final Color MUTED_LIGHT = Color.web("#5e6e82");
    public static final Color MUTED_DARK = Color.web("#93a3b5");

    private static final Map<String, Image> BASE = new ConcurrentHashMap<>();
    private static final Map<String, Image> TINTED = new ConcurrentHashMap<>();

    private Icons() {}

    public static ImageView view(String name, double size, Color color) {
        ImageView iv = new ImageView(tinted(name, color));
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setMouseTransparent(true);
        return iv;
    }

    public static void setGraphic(Labeled control, String name, double size, Color color) {
        if (control == null) {
            return;
        }
        control.setGraphic(view(name, size, color));
        control.setGraphicTextGap(10);
        if (control.getContentDisplay() == null
                || control.getContentDisplay() == ContentDisplay.TEXT_ONLY) {
            control.setContentDisplay(ContentDisplay.LEFT);
        }
    }

    public static Button iconButton(String name, double size, Color color, String tooltip) {
        Button b = new Button();
        b.setGraphic(view(name, size, color));
        b.getStyleClass().add("icon-btn");
        if (tooltip != null && !tooltip.isBlank()) {
            b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        }
        return b;
    }

    public static Color navColor(boolean dark) {
        return dark ? DARK_NAV : LIGHT_NAV;
    }

    public static Color accentColor(boolean dark) {
        return dark ? DARK_ACCENT : LIGHT_ACCENT;
    }

    public static Color mutedColor(boolean dark) {
        return dark ? MUTED_DARK : MUTED_LIGHT;
    }

    public static void clearCache() {
        TINTED.clear();
    }

    private static Image tinted(String name, Color color) {
        Color c = color == null ? LIGHT_NAV : color;
        String key = name + "|" + c.toString() ;
        return TINTED.computeIfAbsent(key, k -> recolor(base(name), c));
    }

    private static Image base(String name) {
        return BASE.computeIfAbsent(name, Icons::loadBase);
    }

    private static Image loadBase(String name) {
        String path = "/icons/png/" + name + ".png";
        try (InputStream in = Icons.class.getResourceAsStream(path)) {
            if (in == null) {
                // 1x1 transparent fallback
                return new WritableImage(1, 1);
            }
            return new Image(in);
        } catch (Exception e) {
            return new WritableImage(1, 1);
        }
    }

    private static Image recolor(Image src, Color color) {
        int w = (int) Math.round(src.getWidth());
        int h = (int) Math.round(src.getHeight());
        if (w <= 0 || h <= 0) {
            return src;
        }
        WritableImage out = new WritableImage(w, h);
        PixelReader reader = src.getPixelReader();
        PixelWriter writer = out.getPixelWriter();
        if (reader == null) {
            return src;
        }
        double r = color.getRed();
        double g = color.getGreen();
        double b = color.getBlue();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color px = reader.getColor(x, y);
                double a = px.getOpacity();
                if (a <= 0.01) {
                    writer.setColor(x, y, Color.TRANSPARENT);
                } else {
                    writer.setColor(x, y, new Color(r, g, b, a));
                }
            }
        }
        return out;
    }
}
