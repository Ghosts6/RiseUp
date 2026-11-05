package com.riseup.gui.ui.components;

import com.riseup.gui.audio.AlarmSoundPlayer;
import com.riseup.gui.audio.AlarmSounds;
import com.riseup.gui.audio.AlarmSounds.SoundOption;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Shared sound dropdown with an inline Play button on the right. */
public class SoundPicker extends HBox {
    private final ComboBox<SoundOption> combo = new ComboBox<>();
    private final Button play = new Button("▶");
    private final AlarmSoundPlayer player = new AlarmSoundPlayer();

    public SoundPicker() {
        getStyleClass().add("sound-picker");
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);

        combo.getItems().addAll(AlarmSounds.all());
        combo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(combo, Priority.ALWAYS);
        combo.setCellFactory(lv -> labeledCell());
        combo.setButtonCell(labeledCell());
        combo.getSelectionModel().select(AlarmSounds.ofKey("classic"));
        combo.valueProperty().addListener((o, a, b) -> player.stop());
        UiKit.openComboDown(combo);

        play.getStyleClass().addAll("secondary-btn", "sound-play-btn");
        play.setTooltip(new Tooltip("Preview this sound"));
        play.setMinWidth(44);
        play.setOnAction(e -> {
            SoundOption opt = combo.getValue();
            if (opt == null) {
                return;
            }
            player.preview(opt.key());
        });

        getChildren().addAll(combo, play);
        sceneProperty().addListener((obs, o, n) -> {
            if (n == null) {
                player.stop();
            }
        });
    }

    private static ListCell<SoundOption> labeledCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SoundOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : AlarmSounds.display(item));
            }
        };
    }

    public String getSoundKey() {
        SoundOption opt = combo.getValue();
        return opt == null ? "classic" : opt.key();
    }

    public void setSoundKey(String key) {
        combo.getSelectionModel().select(AlarmSounds.ofKey(key));
    }

    public void stopPreview() {
        player.stop();
    }
}
