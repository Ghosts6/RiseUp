package com.riseup.gui;

import com.riseup.gui.api.ApiClient;
import com.riseup.gui.api.WsClient;
import com.riseup.gui.theme.ThemeManager;
import com.riseup.gui.ui.MainShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class RiseUpApp extends Application {
    @Override
    public void start(Stage stage) {
        String server = System.getenv().getOrDefault("RISEUP_SERVER", "http://localhost:8000");
        String token = System.getenv().getOrDefault("RISEUP_TOKEN",
                System.getenv().getOrDefault("DEMO_TOKEN", "dev-token"));
        ApiClient api = new ApiClient(server, token);
        WsClient ws = new WsClient(server, token);
        ThemeManager theme = new ThemeManager();
        MainShell shell = new MainShell(api, ws, theme);
        Scene scene = new Scene(shell, 1240, 800);
        var css = getClass().getResource("/css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        theme.apply(scene, false);
        stage.setTitle("RiseUp");
        stage.setScene(scene);
        stage.show();
        ws.connect(
                shell::onLiveEvent,
                () -> shell.setConnectionState(true),
                () -> shell.setConnectionState(false));
        stage.setOnCloseRequest(e -> ws.close());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
