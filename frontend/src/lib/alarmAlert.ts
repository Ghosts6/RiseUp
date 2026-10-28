/** Web Audio tones + Vibration API for the ringing alarm page (F01/F02). */

export type VibrationLevel = "low" | "medium" | "high";

const VIBRATE_MS: Record<VibrationLevel, number[]> = {
  low: [120, 80, 120, 80, 120],
  medium: [280, 120, 280, 120, 280],
  high: [500, 150, 500, 150, 700, 200, 700],
};

type Tone = { freq: number; dur: number; gap: number };

const SOUND_LOOPS: Record<string, Tone[]> = {
  classic: [
    { freq: 880, dur: 0.18, gap: 0.08 },
    { freq: 988, dur: 0.18, gap: 0.08 },
    { freq: 880, dur: 0.18, gap: 0.35 },
  ],
  chimes: [
    { freq: 523, dur: 0.35, gap: 0.05 },
    { freq: 659, dur: 0.35, gap: 0.05 },
    { freq: 784, dur: 0.45, gap: 0.4 },
  ],
  birds: [
    { freq: 1200, dur: 0.08, gap: 0.04 },
    { freq: 1500, dur: 0.08, gap: 0.04 },
    { freq: 1350, dur: 0.1, gap: 0.25 },
  ],
  radar: [
    { freq: 440, dur: 0.5, gap: 0.15 },
    { freq: 660, dur: 0.5, gap: 0.4 },
  ],
  beacon: [
    { freq: 700, dur: 0.25, gap: 0.1 },
    { freq: 700, dur: 0.25, gap: 0.55 },
  ],
};

export class AlarmAlert {
  private ctx: AudioContext | null = null;
  private timer: number | null = null;
  private vibrateTimer: number | null = null;
  private stopped = false;

  start(sound: string, vibration: VibrationLevel | string) {
    this.stop();
    this.stopped = false;
    const tones = SOUND_LOOPS[sound] ?? SOUND_LOOPS.classic;
    const pattern = VIBRATE_MS[(vibration as VibrationLevel)] ?? VIBRATE_MS.medium;

    const playLoop = async () => {
      if (this.stopped) return;
      try {
        if (!this.ctx) this.ctx = new AudioContext();
        if (this.ctx.state === "suspended") await this.ctx.resume();
        let t = this.ctx.currentTime + 0.02;
        for (const tone of tones) {
          const osc = this.ctx.createOscillator();
          const gain = this.ctx.createGain();
          osc.type = sound === "birds" ? "triangle" : sound === "radar" ? "sawtooth" : "sine";
          osc.frequency.value = tone.freq;
          gain.gain.setValueAtTime(0.0001, t);
          gain.gain.exponentialRampToValueAtTime(0.22, t + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.0001, t + tone.dur);
          osc.connect(gain);
          gain.connect(this.ctx.destination);
          osc.start(t);
          osc.stop(t + tone.dur + 0.02);
          t += tone.dur + tone.gap;
        }
        const cycleMs = Math.max(800, (t - this.ctx.currentTime) * 1000);
        this.timer = window.setTimeout(() => void playLoop(), cycleMs);
      } catch {
        // Autoplay / AudioContext may be blocked until a gesture; vibration still helps.
      }
    };

    void playLoop();

    const vibrateLoop = () => {
      if (this.stopped) return;
      try {
        navigator.vibrate?.(pattern);
      } catch {
        /* unsupported */
      }
      const cycle = pattern.reduce((a, b) => a + b, 0) + 400;
      this.vibrateTimer = window.setTimeout(vibrateLoop, cycle);
    };
    vibrateLoop();
  }

  /** Call from a user gesture if the browser suspended autoplay audio. */
  resume() {
    if (this.stopped) return;
    void this.ctx?.resume().catch(() => undefined);
  }

  stop() {
    this.stopped = true;
    if (this.timer != null) window.clearTimeout(this.timer);
    if (this.vibrateTimer != null) window.clearTimeout(this.vibrateTimer);
    this.timer = null;
    this.vibrateTimer = null;
    try {
      navigator.vibrate?.(0);
    } catch {
      /* ignore */
    }
    if (this.ctx) {
      void this.ctx.close().catch(() => undefined);
      this.ctx = null;
    }
  }
}
