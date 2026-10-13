/**
 * ClearPath Web Audio Emergency Siren Synthesizer
 * Generates an authentic, looping ambulance siren using the Web Audio API
 * and triggers device vibration patterns.
 */
class SirenSynthesizer {
  constructor() {
    this.audioCtx = null;
    this.oscillator = null;
    this.gainNode = null;
    this.isPlaying = false;
    this.timerId = null;
    this.vibrateInterval = null;
  }

  init() {
    if (!this.audioCtx) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (AudioContextClass) {
        this.audioCtx = new AudioContextClass();
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  start() {
    this.init();
    if (!this.audioCtx) {
      console.warn('Web Audio API not supported on this device');
      return;
    }

    if (this.isPlaying) return;
    this.isPlaying = true;

    try {
      this.gainNode = this.audioCtx.createGain();
      this.gainNode.gain.setValueAtTime(0.8, this.audioCtx.currentTime);
      this.gainNode.connect(this.audioCtx.destination);

      this.oscillator = this.audioCtx.createOscillator();
      this.oscillator.type = 'sawtooth';

      // Start alternating Hi-Lo Ambulance Siren (960 Hz <-> 770 Hz)
      let hiTone = true;
      const switchTone = () => {
        if (!this.isPlaying || !this.oscillator) return;
        const targetFreq = hiTone ? 960 : 770;
        const now = this.audioCtx.currentTime;
        this.oscillator.frequency.cancelScheduledValues(now);
        this.oscillator.frequency.setTargetAtTime(targetFreq, now, 0.04);
        hiTone = !hiTone;
        this.timerId = setTimeout(switchTone, 500); // 0.5s cadence
      };

      this.oscillator.frequency.setValueAtTime(960, this.audioCtx.currentTime);
      this.oscillator.connect(this.gainNode);
      this.oscillator.start();
      switchTone();

      // Trigger continuous phone vibration
      if ('vibrate' in navigator) {
        navigator.vibrate([500, 200, 500, 200, 500]);
        this.vibrateInterval = setInterval(() => {
          if (this.isPlaying && 'vibrate' in navigator) {
            navigator.vibrate([500, 200, 500, 200, 500]);
          }
        }, 1500);
      }
    } catch (e) {
      console.error('Failed to start audio siren:', e);
    }
  }

  stop() {
    this.isPlaying = false;
    if (this.timerId) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
    if (this.vibrateInterval) {
      clearInterval(this.vibrateInterval);
      this.vibrateInterval = null;
    }
    if ('vibrate' in navigator) {
      navigator.vibrate(0);
    }

    if (this.oscillator) {
      try {
        this.oscillator.stop();
        this.oscillator.disconnect();
      } catch (e) {}
      this.oscillator = null;
    }

    if (this.gainNode) {
      try {
        this.gainNode.disconnect();
      } catch (e) {}
      this.gainNode = null;
    }
  }
}

window.ambulanceSiren = new SirenSynthesizer();
