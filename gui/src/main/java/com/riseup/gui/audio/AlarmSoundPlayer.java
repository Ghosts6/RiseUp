package com.riseup.gui.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plays library WAV assets when present; falls back to synthesized Standard tones. */
public final class AlarmSoundPlayer {
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private Clip clip;

    public synchronized void play(String soundName) {
        start(soundName, true);
    }

    public synchronized void preview(String soundName) {
        start(soundName, false);
    }

    private void start(String soundName, boolean loop) {
        stop();
        String key = soundName == null ? "classic" : soundName.toLowerCase();
        try {
            Clip c = tryOpenFile(key);
            if (c == null) {
                c = openPcm(synthesize(key, loop ? 1.2 : 2.0));
            }
            clip = c;
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                Clip local = clip;
                local.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        playing.set(false);
                    }
                });
            }
            clip.start();
            playing.set(true);
        } catch (Exception e) {
            playing.set(false);
        }
    }

    private Clip tryOpenFile(String key) {
        String path = AlarmSounds.resourcePath(key);
        try (InputStream raw = AlarmSoundPlayer.class.getResourceAsStream(path)) {
            if (raw == null) {
                return null;
            }
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                AudioFormat base = in.getFormat();
                AudioFormat target = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(),
                        16,
                        base.getChannels(),
                        base.getChannels() * 2,
                        base.getSampleRate(),
                        false);
                AudioInputStream pcm = AudioSystem.isConversionSupported(target, base)
                        ? AudioSystem.getAudioInputStream(target, in)
                        : in;
                Clip c = AudioSystem.getClip();
                c.open(pcm);
                return c;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Clip openPcm(byte[] pcm) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(22050f, 16, 1, true, false);
        Clip c = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, format));
        c.open(format, pcm, 0, pcm.length);
        return c;
    }

    public synchronized void stop() {
        playing.set(false);
        if (clip != null) {
            try {
                clip.stop();
                clip.close();
            } catch (Exception ignored) {
            }
            clip = null;
        }
    }

    public boolean isPlaying() {
        return playing.get();
    }

    private static byte[] synthesize(String name, double seconds) {
        float sampleRate = 22050f;
        int totalSamples = (int) (sampleRate * seconds);
        byte[] data = new byte[totalSamples * 2];
        double[] freqs = frequenciesFor(name);
        double beatHz = beatFor(name);
        for (int i = 0; i < totalSamples; i++) {
            double t = i / sampleRate;
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * beatHz * t);
            if (pulse < 0.35) {
                pulse = 0;
            }
            double sample = 0;
            for (double f : freqs) {
                sample += Math.sin(2 * Math.PI * f * t);
            }
            sample = (sample / freqs.length) * pulse * 0.5;
            if (t > seconds - 0.2) {
                sample *= Math.max(0, (seconds - t) / 0.2);
            }
            short val = (short) Math.max(-32767, Math.min(32767, (int) (sample * 32767)));
            data[i * 2] = (byte) (val & 0xff);
            data[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
        }
        return data;
    }

    private static double[] frequenciesFor(String name) {
        return switch (name) {
            case "standard_soft", "silk" -> new double[] {330, 392};
            case "standard_pulse", "radar" -> new double[] {880, 1320};
            case "standard_urgent", "presto" -> new double[] {1000, 1400};
            case "default" -> new double[] {700, 900};
            default -> new double[] {880, 1108}; // standard_beep / classic
        };
    }

    private static double beatFor(String name) {
        return switch (name) {
            case "standard_soft" -> 1.2;
            case "standard_pulse", "radar" -> 3.0;
            case "standard_urgent", "presto" -> 4.5;
            default -> 2.2;
        };
    }
}
