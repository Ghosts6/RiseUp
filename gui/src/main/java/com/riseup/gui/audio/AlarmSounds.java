package com.riseup.gui.audio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared sound catalog for alarms and task/event reminders. */
public final class AlarmSounds {
    public record SoundOption(String key, String label, String category, boolean fileBacked) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final Map<String, SoundOption> BY_KEY = new LinkedHashMap<>();
    private static final List<SoundOption> ALL = new ArrayList<>();

    static {
        // Library — WAV assets under /sounds
        lib("classic", "Classic");
        lib("radar", "Radar");
        lib("apex", "Apex");
        lib("beacon", "Beacon");
        lib("chimes", "Chimes");
        lib("circuit", "Circuit");
        lib("constellation", "Constellation");
        lib("coastline", "Coastline");
        lib("hillside", "Hillside");
        lib("meadow", "Meadow");
        lib("night_owl", "Night Owl");
        lib("opening", "Opening");
        lib("presto", "Presto");
        lib("silk", "Silk");
        lib("slow_rise", "Slow Rise");
        lib("sonar", "Sonar");
        lib("stargaze", "Stargaze");
        lib("summit", "Summit");
        lib("waves", "Waves");
        lib("alert_pulse", "Alert Pulse");
        lib("digital_bell", "Digital Bell");
        lib("gentle_rise", "Gentle Rise");
        lib("clock_burst", "Clock Burst");

        // Imported — converted from Downloads MP3s
        imp("siren_alert", "Siren Alert");
        imp("soft_alert", "Soft Alert");
        imp("extraterrestrial", "Extraterrestrial");
        imp("alien_alert", "Alien Alert");
        imp("ringtone", "Ringtone");
        imp("warning_blip", "Warning Blip");
        imp("scratch_alarm", "Scratch Alarm");
        imp("sharp_alert", "Sharp Alert");
        imp("digital_alert", "Digital Alert");

        // Standard — simple synthesized tones (no file required)
        std("standard_beep", "Standard Beep");
        std("standard_soft", "Standard Soft");
        std("standard_pulse", "Standard Pulse");
        std("standard_urgent", "Standard Urgent");
        std("default", "Default Tone");
    }

    private AlarmSounds() {}

    private static void lib(String key, String label) {
        add(new SoundOption(key, label, "Library", true));
    }

    private static void imp(String key, String label) {
        add(new SoundOption(key, label, "Imported", true));
    }

    private static void std(String key, String label) {
        add(new SoundOption(key, label, "Standard", false));
    }

    private static void add(SoundOption opt) {
        BY_KEY.put(opt.key(), opt);
        ALL.add(opt);
    }

    public static List<SoundOption> all() {
        return List.copyOf(ALL);
    }

    public static List<SoundOption> library() {
        return ALL.stream().filter(o -> "Library".equals(o.category())).toList();
    }

    public static List<SoundOption> imported() {
        return ALL.stream().filter(o -> "Imported".equals(o.category())).toList();
    }

    public static List<SoundOption> standard() {
        return ALL.stream().filter(o -> "Standard".equals(o.category())).toList();
    }

    public static SoundOption ofKey(String key) {
        if (key == null || key.isBlank()) {
            return BY_KEY.get("classic");
        }
        return BY_KEY.getOrDefault(key.toLowerCase(), BY_KEY.get("classic"));
    }

    public static String labelOf(String key) {
        return ofKey(key).label();
    }

    public static String keyOfLabel(String label) {
        if (label == null) {
            return "classic";
        }
        for (SoundOption o : ALL) {
            if (o.label().equalsIgnoreCase(label) || (o.category() + " · " + o.label()).equalsIgnoreCase(label)) {
                return o.key();
            }
        }
        return "classic";
    }

    public static String display(SoundOption o) {
        return o.category() + " · " + o.label();
    }

    public static String resourcePath(String key) {
        return "/sounds/" + key + ".wav";
    }
}
