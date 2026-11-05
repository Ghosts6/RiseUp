package com.riseup.gui.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.gui.api.ApiClient;
import com.riseup.gui.api.WsClient;
import com.riseup.gui.model.LiveEvent;
import com.riseup.gui.theme.ThemeManager;
import com.riseup.gui.ui.components.UiKit;
import com.riseup.gui.ui.pages.AgentPage;
import com.riseup.gui.ui.pages.AlarmFormPage;
import com.riseup.gui.ui.pages.AlarmRingingPage;
import com.riseup.gui.ui.pages.CheckInPage;
import com.riseup.gui.ui.pages.DashboardPage;
import com.riseup.gui.ui.pages.HistoryPage;
import com.riseup.gui.ui.pages.InsightsPage;
import com.riseup.gui.ui.pages.RecommendationsPage;
import com.riseup.gui.ui.pages.SettingsPage;
import com.riseup.gui.ui.pages.TasksPage;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MainShell extends BorderPane {
    private final ApiClient api;
    private final ThemeManager theme;
    private final StackPane content = new StackPane();
    private final StackPane toastLayer = new StackPane();
    private final Label liveLabel = new Label("Offline");
    private final Button themeBtn = new Button("Dark mode");
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, Supplier<javafx.scene.Node>> pages = new LinkedHashMap<>();
    private WsClient ws;

    private static final Map<String, String> NAV_LABELS = Map.of(
            "Dashboard", "⌂  Dashboard",
            "Agent", "✦  AI Agent",
            "New Alarm", "+  New Alarm",
            "Tasks", "☑  Tasks & Events",
            "Insights", "◈  Insights",
            "Recommendations", "◎  Recommendations",
            "History", "◷  History",
            "Settings", "⚙  Settings"
    );

    public MainShell(ApiClient api, WsClient ws, ThemeManager theme) {
        this.api = api;
        this.theme = theme;
        this.ws = ws;

        pages.put("Dashboard", () -> new DashboardPage(api, this));
        pages.put("Agent", () -> new AgentPage(api, this));
        pages.put("New Alarm", () -> new AlarmFormPage(api, this, null));
        pages.put("Ringing", () -> new AlarmRingingPage(api, this, null));
        pages.put("Tasks", () -> new TasksPage(api, this));
        pages.put("Insights", () -> new InsightsPage(api, this));
        pages.put("Recommendations", () -> new RecommendationsPage(api, this));
        pages.put("History", () -> new HistoryPage(api, this));
        pages.put("Settings", () -> new SettingsPage(api, this));

        getStyleClass().add("shell");
        setTop(buildHeader());
        setLeft(buildNav());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("content-scroll");

        toastLayer.setMouseTransparent(true);
        StackPane center = new StackPane(scroll, toastLayer);
        setCenter(center);

        navigate("Dashboard");
    }

    public void attachWs(WsClient wsClient) {
        this.ws = wsClient;
    }

    public void toastOk(String message) {
        UiKit.showToast(toastLayer, message, false);
    }

    public void toastError(String message) {
        UiKit.showToast(toastLayer, message, true);
    }

    private HBox buildHeader() {
        HBox header = new HBox(14);
        header.getStyleClass().add("topbar");
        header.setPadding(new Insets(14, 22, 14, 20));
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView logo = new ImageView();
        var logoUrl = getClass().getResource("/img/RiseUp.jpg");
        if (logoUrl != null) {
            logo.setImage(new Image(logoUrl.toExternalForm(), 46, 46, true, true));
            logo.setFitWidth(46);
            logo.setFitHeight(46);
            Rectangle clip = new Rectangle(46, 46);
            clip.setArcWidth(16);
            clip.setArcHeight(16);
            logo.setClip(clip);
            logo.getStyleClass().add("brand-mark");
        }

        Label brand = new Label("RiseUp");
        brand.getStyleClass().add("brand");

        Label tagline = new Label("WAKE WITH INTENT");
        tagline.getStyleClass().add("brand-tagline");

        VBox brandCol = new VBox(1, brand, tagline);
        brandCol.setAlignment(Pos.CENTER_LEFT);

        liveLabel.getStyleClass().addAll("badge", "badge-err");

        themeBtn.getStyleClass().add("theme-toggle");
        themeBtn.setText("☾  Dark mode");
        themeBtn.setOnAction(e -> {
            if (getScene() != null) {
                theme.toggle(getScene());
                themeBtn.setText(theme.isDark() ? "☀  Light mode" : "☾  Dark mode");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(logo, brandCol, spacer, liveLabel, themeBtn);
        return header;
    }

    private VBox buildNav() {
        VBox nav = new VBox(4);
        nav.getStyleClass().add("sidebar");
        nav.setPadding(new Insets(18, 12, 24, 12));
        nav.setPrefWidth(220);

        Label section = new Label("NAVIGATE");
        section.getStyleClass().add("nav-section");
        nav.getChildren().add(section);

        for (String name : pages.keySet()) {
            if ("Ringing".equals(name)) {
                continue;
            }
            Button b = new Button(NAV_LABELS.getOrDefault(name, name));
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> navigate(name));
            navButtons.put(name, b);
            nav.getChildren().add(b);
        }
        return nav;
    }

    public void navigate(String name) {
        Supplier<javafx.scene.Node> supplier = pages.get(name);
        if (supplier == null) {
            return;
        }
        var node = supplier.get();
        content.getChildren().setAll(node);

        node.setOpacity(0);
        node.setTranslateY(8);
        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), node);
        slide.setFromY(8);
        slide.setToY(0);
        fade.play();
        slide.play();

        navButtons.forEach((k, btn) -> {
            btn.getStyleClass().remove("active");
            if (k.equals(name)) {
                btn.getStyleClass().add("active");
            }
        });
    }

    public void openAlarmEditor(Integer id) {
        var node = new AlarmFormPage(api, this, id);
        content.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setFromValue(0.5);
        fade.setToValue(1);
        fade.play();
        navButtons.forEach((k, btn) -> {
            btn.getStyleClass().remove("active");
            if ("New Alarm".equals(k)) {
                btn.getStyleClass().add("active");
            }
        });
    }

    public void openRinging(Integer alarmId) {
        var node = new AlarmRingingPage(api, this, alarmId);
        content.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(0.4);
        fade.setToValue(1);
        fade.play();
    }

    public void openCheckIn(Integer alarmId) {
        var node = new CheckInPage(api, this, alarmId);
        content.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(0.4);
        fade.setToValue(1);
        fade.play();
    }

    public void onLiveEvent(JsonNode event) {
        Platform.runLater(() -> {
            LiveEvent live = LiveEvent.from(event);
            liveLabel.setText("● Live · " + live.displayLine());
            liveLabel.getStyleClass().setAll("badge", "badge-live");
            DashboardPage.pushLive(live.displayLine());
            if (live.isRingingAlarm()) {
                openRinging(live.subjectId());
            }
        });
    }
}
