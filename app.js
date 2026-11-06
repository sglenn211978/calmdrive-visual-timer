/**
 * CalmDrive — Android Auto Coolwalk Visual Timer Prototype
 * Models MediaBrowserServiceCompat + MediaSessionCompat (512x512 Artwork + Metadata + CustomActions)
 */

const PALETTES = {
  traffic: {
    name: 'Traffic Light',
    high: { main: '#34d399', glow: 'rgba(52, 211, 153, 0.28)', dot: '🟢' },     // > 40% left
    mid:  { main: '#fbbf24', glow: 'rgba(251, 191, 36, 0.28)', dot: '🟡' },     // 15% - 40% left
    low:  { main: '#fb7185', glow: 'rgba(251, 113, 133, 0.30)', dot: '🟠' },    // < 15% left (Soft coral instead of harsh alarm red)
    done: { main: '#a78bfa', glow: 'rgba(167, 139, 250, 0.32)', dot: '🎉' }
  },
  ocean: {
    name: 'Calm Ocean',
    high: { main: '#38bdf8', glow: 'rgba(56, 189, 248, 0.28)', dot: '🔵' },
    mid:  { main: '#2dd4bf', glow: 'rgba(45, 212, 191, 0.28)', dot: '🟢' },
    low:  { main: '#c084fc', glow: 'rgba(192, 132, 252, 0.30)', dot: '🟣' },
    done: { main: '#34d399', glow: 'rgba(52, 211, 153, 0.32)', dot: '✨' }
  },
  sunshine: {
    name: 'High-Contrast',
    high: { main: '#facc15', glow: 'rgba(250, 204, 21, 0.30)', dot: '🟡' },
    mid:  { main: '#22d3ee', glow: 'rgba(34, 211, 238, 0.30)', dot: '🔵' },
    low:  { main: '#f472b6', glow: 'rgba(244, 114, 182, 0.32)', dot: '🟣' },
    done: { main: '#4ade80', glow: 'rgba(74, 222, 128, 0.32)', dot: '🌟' }
  }
};

const DEFAULT_ROUTINES = [
  {
    id: 'school-dropoff',
    firstTitle: 'School Drop-off',
    firstIcon: '🏫',
    firstMins: 15,
    thenTitle: 'Drop-off Hug & Backpack',
    thenIcon: '🎒',
    thenMins: 2,
    destStreet: 'Oak Park Elementary'
  },
  {
    id: 'park-trip',
    firstTitle: 'Drive to Park',
    firstIcon: '🌳',
    firstMins: 12,
    thenTitle: 'Swings & Playground',
    thenIcon: '🛝',
    thenMins: 30,
    destStreet: 'Cedar Creek Park'
  },
  {
    id: 'going-home',
    firstTitle: 'Heading Home',
    firstIcon: '🏡',
    firstMins: 20,
    thenTitle: 'Snack & Quiet Time',
    thenIcon: '🍎',
    thenMins: 15,
    destStreet: 'Maple Street Home'
  },
  {
    id: 'grocery-pickup',
    firstTitle: 'Grocery Curbside Wait',
    firstIcon: '🛒',
    firstMins: 8,
    thenTitle: 'Pick a Car Sticker',
    thenIcon: '⭐',
    thenMins: 2,
    destStreet: 'Market Square Lane'
  },
  {
    id: 'drivethru-wait',
    firstTitle: 'Drive-Thru Line',
    firstIcon: '🍟',
    firstMins: 5,
    thenTitle: 'Eat in Car',
    thenIcon: '🧃',
    thenMins: 10,
    destStreet: 'Main St Plaza'
  }
];

/* =========================================================
   GENTLE HARMONIC AUDIO & TTS ENGINE
   ========================================================= */
class TransitionAudioEngine {
  constructor() {
    this.ctx = null;
  }

  ensureContext() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  /**
   * Plays a soft, warm marimba / singing-bowl chime (non-startling for sensory sensitivity)
   */
  playGentleChime(type = 'milestone') {
    try {
      this.ensureContext();
      if (!this.ctx) return;

      const now = this.ctx.currentTime;
      // C major pentatonic warm frequencies
      const notes = type === 'done'
        ? [523.25, 659.25, 783.99, 1046.5] // C5 - E5 - G5 - C6 ascending celebration
        : [440, 554.37, 659.25];            // A4 - C#5 - E5 soft triad

      notes.forEach((freq, idx) => {
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, now + idx * 0.14);

        gain.gain.setValueAtTime(0.001, now + idx * 0.14);
        gain.gain.exponentialRampToValueAtTime(0.16, now + idx * 0.14 + 0.04);
        gain.gain.exponentialRampToValueAtTime(0.0008, now + idx * 0.14 + 1.15);

        osc.connect(gain);
        gain.connect(this.ctx.destination);

        osc.start(now + idx * 0.14);
        osc.stop(now + idx * 0.14 + 1.2);
      });
    } catch (e) {
      console.warn('Audio chime skipped:', e);
    }
  }

  speakCalmWarning(text) {
    if (!('speechSynthesis' in window)) return;
    try {
      window.speechSynthesis.cancel();
      const utter = new SpeechSynthesisUtterance(text);
      utter.rate = 0.94; // Slightly slower, calmer cadence
      utter.pitch = 1.02;
      utter.volume = 0.9;
      window.speechSynthesis.speak(utter);
    } catch (e) {
      console.warn('TTS skipped:', e);
    }
  }
}

/* =========================================================
   512x512 ANDROID AUTO ALBUM ARTWORK RENDERER
   ========================================================= */
class AutoArtworkRenderer {
  static render(canvas, state) {
    const ctx = canvas.getContext('2d');
    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    const palette = PALETTES[state.palette] || PALETTES.traffic;
    const ratio = state.totalSec > 0 ? Math.max(0, Math.min(1, state.remainingSec / state.totalSec)) : 0;

    let stageColor = palette.high;
    if (state.remainingSec <= 0) {
      stageColor = palette.done;
    } else if (ratio <= 0.18) {
      stageColor = palette.low;
    } else if (ratio <= 0.45) {
      stageColor = palette.mid;
    }

    // 1. Deep High-Contrast Card Background (Readable in car glare)
    const bgGrad = ctx.createRadialGradient(W * 0.5, H * 0.42, 20, W * 0.5, H * 0.5, W * 0.72);
    bgGrad.addColorStop(0, '#192132');
    bgGrad.addColorStop(1, '#0c1018');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, W, H);

    // Subtle ambient ring glow
    ctx.save();
    ctx.fillStyle = stageColor.glow;
    ctx.beginPath();
    ctx.arc(W * 0.5, H * 0.44, W * 0.36, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // 2. Draw Style-Specific Visual Timer Graphic
    if (state.remainingSec <= 0) {
      this.drawCelebration(ctx, W, H, state, stageColor);
    } else if (state.visualStyle === 'disc') {
      this.drawShrinkingDisc(ctx, W, H, state, ratio, stageColor);
    } else if (state.visualStyle === 'journey') {
      this.drawCarJourney(ctx, W, H, state, ratio, stageColor);
    } else if (state.visualStyle === 'blocks') {
      this.drawCalmBlocks(ctx, W, H, state, ratio, stageColor);
    }
  }

  static drawShrinkingDisc(ctx, W, H, state, ratio, stageColor) {
    const cx = W * 0.5;
    const cy = H * 0.5;
    const outerR = W * 0.43;
    const innerR = W * 0.22;

    // 12 Clock Tick Marks around the disc
    for (let i = 0; i < 12; i++) {
      const angle = (i / 12) * Math.PI * 2 - Math.PI / 2;
      const isQuarter = i % 3 === 0;
      const r1 = outerR + 6;
      const r2 = outerR + (isQuarter ? 24 : 16);
      ctx.strokeStyle = isQuarter ? 'rgba(255,255,255,0.6)' : 'rgba(255,255,255,0.25)';
      ctx.lineWidth = isQuarter ? 5 : 3;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(angle) * r1, cy + Math.sin(angle) * r1);
      ctx.lineTo(cx + Math.cos(angle) * r2, cy + Math.sin(angle) * r2);
      ctx.stroke();
    }

    // Background Empty Track Ring
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
    ctx.fill();

    // Shrinking Time Timer Pie Wedge (Starts at 12 o'clock, shrinks clockwise)
    const startAngle = -Math.PI / 2;
    const endAngle = startAngle + ratio * Math.PI * 2;

    ctx.save();
    ctx.fillStyle = stageColor.main;
    ctx.shadowColor = stageColor.main;
    ctx.shadowBlur = 20;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, outerR, startAngle, endAngle, false);
    ctx.closePath();
    ctx.fill();
    ctx.restore();

    // Center Hub Circle (Holds Activity Icon & Time Readout)
    ctx.fillStyle = '#0f1523';
    ctx.beginPath();
    ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
    ctx.fill();

    ctx.lineWidth = 5;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
    ctx.stroke();

    // Activity Icon in Center
    const iconSize = state.numberMode === 'none' ? Math.round(W * 0.18) : Math.round(W * 0.14);
    ctx.font = `${iconSize}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const iconY = state.numberMode === 'none' ? cy : cy - W * 0.038;
    ctx.fillText(state.currentIcon || '🚗', cx, iconY);

    // Bold Number Readout below Icon in Center Hub
    if (state.numberMode !== 'none') {
      ctx.fillStyle = '#ffffff';
      ctx.font = `800 ${Math.round(W * 0.075)}px "Outfit", sans-serif`;
      ctx.fillText(state.formattedShortTime, cx, cy + W * 0.065);
    }
  }

  static drawCarJourney(ctx, W, H, state, ratio, stageColor) {
    const progress = 1 - ratio; // 0.0 (start) -> 1.0 (arrived)
    const padX = W * 0.13;
    const trackW = W - padX * 2;
    const trackY = H * 0.46;

    // Header Activity Title + Time Pill
    ctx.textAlign = 'center';
    ctx.fillStyle = '#ffffff';
    ctx.font = `800 ${Math.round(W * 0.075)}px "Outfit", sans-serif`;
    const topLabel = state.numberMode === 'none'
      ? `${state.currentIcon} ${state.currentTitle}`
      : `${state.formattedShortTime} • ${state.currentIcon}`;
    ctx.fillText(topLabel, W * 0.5, H * 0.19);

    // Road Base Track
    ctx.lineCap = 'round';
    ctx.lineWidth = 34;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.beginPath();
    ctx.moveTo(padX, trackY);
    ctx.lineTo(padX + trackW, trackY);
    ctx.stroke();

    // Dashed Center Highway Stripes
    ctx.save();
    ctx.setLineDash([14, 14]);
    ctx.lineWidth = 3;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
    ctx.beginPath();
    ctx.moveTo(padX + 20, trackY);
    ctx.lineTo(padX + trackW - 20, trackY);
    ctx.stroke();
    ctx.restore();

    // Completed Progress Bar Fill on Road
    const currentX = padX + trackW * progress;
    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineWidth = 22;
    ctx.strokeStyle = stageColor.main;
    ctx.shadowColor = stageColor.main;
    ctx.shadowBlur = 16;
    ctx.beginPath();
    ctx.moveTo(padX, trackY);
    ctx.lineTo(Math.max(padX + 4, currentX), trackY);
    ctx.stroke();
    ctx.restore();

    // Milestone Checkpoints (Start, Halfway Star, Destination)
    const checkpoints = [
      { x: padX, label: 'Start', icon: '🏁' },
      { x: padX + trackW * 0.5, label: 'Halfway', icon: progress >= 0.5 ? '⭐' : '⚪' },
      { x: padX + trackW, label: 'Goal', icon: state.currentIcon || '🏫' }
    ];

    checkpoints.forEach(cp => {
      ctx.fillStyle = '#141a29';
      ctx.beginPath();
      ctx.arc(cp.x, trackY - 56, 26, 0, Math.PI * 2);
      ctx.fill();
      ctx.lineWidth = 2.5;
      ctx.strokeStyle = 'rgba(255,255,255,0.2)';
      ctx.stroke();

      ctx.font = '26px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(cp.icon, cp.x, trackY - 56);
    });

    // Moving Car Bubble on the Track
    ctx.save();
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = 'rgba(0,0,0,0.6)';
    ctx.shadowBlur = 12;
    ctx.beginPath();
    ctx.arc(currentX, trackY, 28, 0, Math.PI * 2);
    ctx.fill();

    ctx.lineWidth = 5;
    ctx.strokeStyle = stageColor.main;
    ctx.stroke();

    ctx.font = '30px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    // Flip car emoji horizontally so it faces right toward destination
    ctx.translate(currentX, trackY);
    ctx.scale(-1, 1);
    ctx.fillText('🚗', 0, 2);
    ctx.restore();

    // Visual Progress Dots below Road
    const totalDots = 5;
    const activeDots = Math.ceil(ratio * totalDots);
    for (let i = 0; i < totalDots; i++) {
      const dx = W * 0.34 + i * (W * 0.08);
      const dy = trackY + 66;
      ctx.fillStyle = i < activeDots ? stageColor.main : 'rgba(255,255,255,0.12)';
      ctx.beginPath();
      ctx.arc(dx, dy, 9, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  static drawCalmBlocks(ctx, W, H, state, ratio, stageColor) {
    // 10 Discrete Visual Blocks (2 rows of 5) so kids see countable chunks instead of continuous motion
    const totalBlocks = 10;
    const litBlocks = Math.ceil(ratio * totalBlocks);

    // Top Header Icon + Time
    ctx.textAlign = 'center';
    ctx.fillStyle = '#ffffff';
    ctx.font = `800 ${Math.round(W * 0.075)}px "Outfit", sans-serif`;
    const headerText = state.numberMode === 'none'
      ? `${state.currentIcon} ${litBlocks} steps left`
      : `${state.currentIcon}  ${state.formattedShortTime}`;
    ctx.fillText(headerText, W * 0.5, H * 0.19);

    const cols = 5;
    const rows = 2;
    const gap = 14;
    const gridW = W * 0.78;
    const gridH = H * 0.38;
    const startX = (W - gridW) / 2;
    const startY = H * 0.28;
    const cellW = (gridW - gap * (cols - 1)) / cols;
    const cellH = (gridH - gap * (rows - 1)) / rows;

    for (let i = 0; i < totalBlocks; i++) {
      const r = Math.floor(i / cols);
      const c = i % cols;
      const x = startX + c * (cellW + gap);
      const y = startY + r * (cellH + gap);
      const isLit = i < litBlocks;

      ctx.save();
      ctx.fillStyle = isLit ? stageColor.main : 'rgba(255, 255, 255, 0.07)';
      if (isLit) {
        ctx.shadowColor = stageColor.main;
        ctx.shadowBlur = 12;
      }
      this.roundRect(ctx, x, y, cellW, cellH, 16);
      ctx.fill();

      // Inner star/dot for tactile clarity
      ctx.fillStyle = isLit ? 'rgba(10, 16, 28, 0.45)' : 'rgba(255, 255, 255, 0.12)';
      ctx.font = '700 22px "Outfit", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(`${i + 1}`, x + cellW / 2, y + cellH / 2);
      ctx.restore();
    }
  }

  static drawCelebration(ctx, W, H, state, stageColor) {
    const cx = W * 0.5;
    const cy = H * 0.5;

    ctx.save();
    ctx.fillStyle = stageColor.main;
    ctx.beginPath();
    ctx.arc(cx, cy, W * 0.36, 0, Math.PI * 2);
    ctx.fill();

    ctx.font = '100px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('🎉', cx, cy - 14);

    ctx.fillStyle = '#ffffff';
    ctx.font = '800 42px "Outfit", sans-serif';
    ctx.fillText('All Done!', cx, cy + 85);
    ctx.restore();
  }

  static drawBottomFooter(ctx, W, H, state, stageColor) {
    const pillW = W * 0.86;
    const pillH = 64;
    const pillX = (W - pillW) / 2;
    const pillY = H - pillH - 22;

    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    this.roundRect(ctx, pillX, pillY, pillW, pillH, 32);
    ctx.fill();

    ctx.lineWidth = 2;
    ctx.strokeStyle = state.isPaused ? '#fbbf24' : 'rgba(255, 255, 255, 0.14)';
    ctx.stroke();

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '700 23px "Inter", sans-serif';
    ctx.fillStyle = '#e2e8f0';

    let footerText = '';
    if (state.isPaused) {
      footerText = '⏸ Timer Paused';
    } else if (state.step === 'first' && state.thenTitle) {
      footerText = `Then: ${state.thenIcon || '✨'} ${state.thenTitle}`;
    } else if (state.step === 'then') {
      footerText = `✨ Current Step: ${state.currentIcon} ${state.currentTitle}`;
    } else {
      footerText = `${stageColor.dot} ${state.currentTitle}`;
    }

    ctx.fillText(footerText, W * 0.5, pillY + pillH / 2, pillW - 28);
    ctx.restore();
  }

  static roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }
}

/* =========================================================
   GOOGLE MAPS NAVIGATION SIMULATOR (LEFT 65% OF CAR SCREEN)
   ========================================================= */
class MapsNavigatorSim {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.offset = 0;
    this.trafficState = 'smooth'; // 'smooth' | 'delay' | 'faster'
    this.etaSeconds = 15 * 60;
  }

  draw(progressRatio) {
    const ctx = this.ctx;
    const W = this.canvas.width;
    const H = this.canvas.height;
    this.offset = (this.offset + 0.8) % 60;

    // Dark Google Maps Navigation Vector Background
    ctx.fillStyle = '#171d29';
    ctx.fillRect(0, 0, W, H);

    // City Blocks / Parks
    ctx.fillStyle = '#1b2826';
    ctx.fillRect(W * 0.08, H * 0.18, 170, 120);
    ctx.fillRect(W * 0.65, H * 0.25, 190, 150);

    // Secondary City Grid Roads
    ctx.strokeStyle = '#242c3d';
    ctx.lineWidth = 14;
    for (let x = 80; x < W; x += 140) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x - 40, H);
      ctx.stroke();
    }
    for (let y = 90; y < H; y += 110) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(W, y + 20);
      ctx.stroke();
    }

    // Main Navigation Route Path (3D Perspective Road)
    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    // Route Border
    ctx.strokeStyle = '#1e3a8a';
    ctx.lineWidth = 34;
    ctx.beginPath();
    ctx.moveTo(W * 0.45, H);
    ctx.quadraticCurveTo(W * 0.46, H * 0.55, W * 0.62, H * 0.18);
    ctx.stroke();

    // Route Fill (Changes tint slightly if traffic delay simulated)
    ctx.strokeStyle = this.trafficState === 'delay' ? '#f59e0b' : '#4285F4';
    ctx.lineWidth = 24;
    ctx.beginPath();
    ctx.moveTo(W * 0.45, H);
    ctx.quadraticCurveTo(W * 0.46, H * 0.55, W * 0.62, H * 0.18);
    ctx.stroke();

    // Animated Route Chevrons
    ctx.setLineDash([10, 26]);
    ctx.lineDashOffset = -this.offset;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.55)';
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(W * 0.45, H);
    ctx.quadraticCurveTo(W * 0.46, H * 0.55, W * 0.62, H * 0.18);
    ctx.stroke();
    ctx.restore();

    // Navigation Puck (Blue/White Chevron Circle at bottom center)
    const puckX = W * 0.465;
    const puckY = H * 0.68;
    ctx.save();
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = 'rgba(0,0,0,0.65)';
    ctx.shadowBlur = 16;
    ctx.beginPath();
    ctx.arc(puckX, puckY, 22, 0, Math.PI * 2);
    ctx.fill();

    // Blue Navigation Arrow inside Puck
    ctx.fillStyle = '#4285F4';
    ctx.beginPath();
    ctx.moveTo(puckX, puckY - 12);
    ctx.lineTo(puckX + 10, puckY + 10);
    ctx.lineTo(puckX, puckY + 5);
    ctx.lineTo(puckX - 10, puckY + 10);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }
}

/* =========================================================
   MAIN VISUAL TIMER CONTROLLER
   ========================================================= */
class VisualTimerApp {
  constructor() {
    this.audio = new TransitionAudioEngine();
    this.carCanvas = document.getElementById('carArtworkCanvas');
    this.phoneCanvas = document.getElementById('phonePreviewCanvas');
    this.mapsSim = new MapsNavigatorSim(document.getElementById('mapsCanvas'));

    this.routines = this.loadRoutines();
    this.activeRoutine = this.routines[0];

    // Timer State
    this.step = 'first'; // 'first' | 'then'
    this.totalSec = this.activeRoutine.firstMins * 60;
    this.remainingSec = this.totalSec;
    this.isPaused = true;
    this.simSpeed = 1; // 1x, 10x, 30x

    // Kid Sensory & Visual Preferences
    this.visualStyle = 'disc';     // 'disc' | 'journey' | 'blocks'
    this.numberMode = 'rounded';   // 'rounded' | 'exact' | 'none'
    this.palette = 'traffic';      // 'traffic' | 'ocean' | 'sunshine'
    this.chimesEnabled = true;
    this.voiceEnabled = true;
    this.mapsSyncEnabled = false;
    this.activeDrawerTab = 'routines';

    // Track fired milestones for current run so they trigger once
    this.firedMilestones = new Set();

    this.bindEvents();
    this.renderRoutinesList();
    this.renderDrawerContent();
    this.startRoutine(this.activeRoutine, false, false);

    // Main Loop (100ms tick for smooth progress & speed multipliers)
    this.lastTick = performance.now();
    requestAnimationFrame((t) => this.loop(t));
  }

  loadRoutines() {
    try {
      const saved = localStorage.getItem('calmdrive_routines_v1');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch (e) {
      console.warn(e);
    }
    return [...DEFAULT_ROUTINES];
  }

  saveRoutines() {
    try {
      localStorage.setItem('calmdrive_routines_v1', JSON.stringify(this.routines));
    } catch (e) {
      console.warn(e);
    }
  }

  startRoutine(routine, announce = true, autoStart = false) {
    this.activeRoutine = routine;
    this.step = 'first';
    this.totalSec = routine.firstMins * 60;
    this.remainingSec = this.totalSec;
    this.mapsSim.etaSeconds = this.totalSec;
    this.mapsSim.trafficState = 'smooth';
    this.isPaused = !autoStart;
    this.firedMilestones.clear();

    document.getElementById('mapsDestBannerName').textContent =
      routine.destStreet || routine.firstTitle;

    this.renderRoutinesList();
    this.renderDrawerContent();
    this.updateAllUI();

    if (announce) {
      if (this.chimesEnabled) this.audio.playGentleChime('milestone');
      if (this.voiceEnabled) {
        const thenPart = routine.thenTitle ? `. Then: ${routine.thenTitle}.` : '';
        const prompt = autoStart ? 'Starting timer' : 'Timer ready';
        this.audio.speakCalmWarning(
          `${prompt}: ${routine.firstMins} minutes for ${routine.firstTitle}${thenPart}`
        );
      }
    }
  }

  resetTimer() {
    this.step = 'first';
    this.remainingSec = this.totalSec;
    this.mapsSim.etaSeconds = this.totalSec;
    this.mapsSim.trafficState = 'smooth';
    this.isPaused = true;
    this.firedMilestones.clear();
    this.renderRoutinesList();
    this.updateAllUI();
    this.showCarToast('↺', 'Timer Reset', `${this.getCurrentTitle()} reset to ${Math.round(this.totalSec / 60)} min (Tap Play to start)`);
  }

  startQuickTimer(mins, autoStart = false) {
    const customQuick = {
      id: `quick-${mins}`,
      firstTitle: `${mins} Minute Timer`,
      firstIcon: '⏱️',
      firstMins: mins,
      thenTitle: 'All Done!',
      thenIcon: '🌟',
      thenMins: 0,
      destStreet: 'Current Route'
    };
    this.startRoutine(customQuick, true, autoStart);
  }

  switchToThenStep() {
    if (!this.activeRoutine.thenTitle || !this.activeRoutine.thenMins) {
      this.remainingSec = 0;
      this.updateAllUI();
      return;
    }
    this.step = 'then';
    this.totalSec = this.activeRoutine.thenMins * 60;
    this.remainingSec = this.totalSec;
    this.isPaused = false;
    this.firedMilestones.clear();

    if (this.chimesEnabled) this.audio.playGentleChime('done');
    if (this.voiceEnabled) {
      this.audio.speakCalmWarning(
        `First step complete! Now it is time for ${this.activeRoutine.thenTitle}, for ${this.activeRoutine.thenMins} minutes.`
      );
    }
    this.showCarToast(
      this.activeRoutine.thenIcon || '✨',
      `Now: ${this.activeRoutine.thenTitle}`,
      `${this.activeRoutine.thenMins} min timer started automatically`
    );
    this.updateAllUI();
  }

  adjustTime(deltaSeconds) {
    this.remainingSec = Math.max(0, this.remainingSec + deltaSeconds);
    if (this.remainingSec > this.totalSec) {
      this.totalSec = this.remainingSec;
    }
    if (this.mapsSyncEnabled && this.step === 'first') {
      this.mapsSim.etaSeconds = this.remainingSec;
    }
    this.updateAllUI();
  }

  simulateMapsTraffic(deltaMinutes, mode) {
    const deltaSec = deltaMinutes * 60;
    this.mapsSim.etaSeconds = Math.max(60, this.mapsSim.etaSeconds + deltaSec);
    this.mapsSim.trafficState = mode;

    if (this.mapsSyncEnabled && this.step === 'first') {
      this.remainingSec = this.mapsSim.etaSeconds;
      if (this.remainingSec > this.totalSec) {
        this.totalSec = this.remainingSec;
      }
      const sign = deltaMinutes > 0 ? `+${deltaMinutes} min traffic` : `${deltaMinutes} min faster route`;
      this.showCarToast(
        '🛰️',
        `Maps ETA Synced (${sign})`,
        `Visual timer smoothly adjusted to match arrival time`
      );
    } else {
      this.showCarToast(
        '🗺️',
        `Google Maps ETA changed (${deltaMinutes > 0 ? '+' : ''}${deltaMinutes}m)`,
        `Turn on "Sync Timer to Maps ETA" to auto-adjust the timer`
      );
    }
    this.updateAllUI();
  }

  loop(now) {
    const dt = (now - this.lastTick) / 1000;
    this.lastTick = now;

    if (!this.isPaused && this.remainingSec > 0) {
      const elapsed = dt * this.simSpeed;
      this.remainingSec = Math.max(0, this.remainingSec - elapsed);

      // Keep Google Maps ETA ticking down in tandem
      this.mapsSim.etaSeconds = Math.max(0, this.mapsSim.etaSeconds - elapsed);

      this.checkMilestones();
    }

    this.mapsSim.draw(this.totalSec > 0 ? 1 - this.remainingSec / this.totalSec : 1);
    this.updateAllUI();

    requestAnimationFrame((t) => this.loop(t));
  }

  checkMilestones() {
    const enabledChecks = Array.from(
      document.querySelectorAll('.milestone-chips input[type="checkbox"]:checked')
    ).map((el) => el.dataset.milestone);

    const sec = Math.ceil(this.remainingSec);
    const halfSec = Math.round(this.totalSec / 2);

    // Halfway check
    if (
      enabledChecks.includes('halfway') &&
      this.totalSec >= 120 &&
      sec <= halfSec &&
      sec > halfSec - 3 &&
      !this.firedMilestones.has('halfway')
    ) {
      this.firedMilestones.add('halfway');
      this.triggerMilestoneAlert(
        '⭐ Halfway There!',
        `Halfway to ${this.getCurrentTitle()}`,
        'milestone'
      );
    }

    // Standard minute milestones (300s = 5m, 120s = 2m, 60s = 1m)
    [300, 120, 60].forEach((mSec) => {
      const key = String(mSec);
      if (
        enabledChecks.includes(key) &&
        this.totalSec > mSec + 15 &&
        sec <= mSec &&
        sec > mSec - 3 &&
        !this.firedMilestones.has(key)
      ) {
        this.firedMilestones.add(key);
        const mins = Math.round(mSec / 60);
        const label = mins === 1 ? '1 Minute Left' : `${mins} Minutes Left`;
        const thenHint =
          this.step === 'first' && this.activeRoutine.thenTitle
            ? `Then: ${this.activeRoutine.thenTitle}`
            : 'Almost finished!';
        this.triggerMilestoneAlert(`🔔 ${label}`, thenHint, 'milestone');
      }
    });

    // Completion (0s)
    if (this.remainingSec <= 0 && !this.firedMilestones.has('0')) {
      this.firedMilestones.add('0');
      if (this.step === 'first' && this.activeRoutine.thenTitle && this.activeRoutine.thenMins > 0) {
        // Auto-advance to "Then" step after brief celebration pause
        this.triggerMilestoneAlert(
          `🎉 Arrived: ${this.activeRoutine.firstTitle}!`,
          `Up Next: ${this.activeRoutine.thenIcon} ${this.activeRoutine.thenTitle}`,
          'done'
        );
        setTimeout(() => {
          if (this.remainingSec <= 0 && this.step === 'first') {
            this.switchToThenStep();
          }
        }, 2800);
      } else if (enabledChecks.includes('0')) {
        this.triggerMilestoneAlert(
          `🎉 All Done: ${this.getCurrentTitle()}!`,
          'Great job handling the transition!',
          'done'
        );
      }
    }
  }

  triggerMilestoneAlert(title, subtitle, chimeType = 'milestone') {
    this.showCarToast('🔔', title, subtitle);
    if (this.chimesEnabled) {
      this.audio.playGentleChime(chimeType);
    }
    if (this.voiceEnabled) {
      const cleanSpeech = `${title.replace(/[^\w\s:!.,-]/g, '')}. ${subtitle.replace(/[^\w\s:!.,-]/g, '')}`;
      this.audio.speakCalmWarning(cleanSpeech);
    }
  }

  showCarToast(icon, title, sub) {
    const toast = document.getElementById('carMilestoneToast');
    document.getElementById('carToastIcon').textContent = icon;
    document.getElementById('carToastTitle').textContent = title;
    document.getElementById('carToastSub').textContent = sub;
    toast.classList.remove('hidden');

    clearTimeout(this.toastTimeout);
    this.toastTimeout = setTimeout(() => {
      toast.classList.add('hidden');
    }, 4200);
  }

  getCurrentTitle() {
    return this.step === 'first' ? this.activeRoutine.firstTitle : this.activeRoutine.thenTitle;
  }

  getCurrentIcon() {
    return this.step === 'first' ? this.activeRoutine.firstIcon : this.activeRoutine.thenIcon;
  }

  formatTimeForKid(sec) {
    const s = Math.ceil(sec);
    if (s <= 0) return 'Done!';

    if (this.numberMode === 'exact') {
      const m = Math.floor(s / 60);
      const rem = s % 60;
      return `${m}:${rem < 10 ? '0' : ''}${rem}`;
    }

    // Low-Anxiety Rounded Mode:
    // Above 60s -> rounded minutes ("12 min"). Under 60s -> calm 10s steps ("40s", "30s", "20s", "10s")
    if (s > 60) {
      const mins = Math.ceil(s / 60);
      return `${mins} min`;
    }
    if (s > 10) {
      return `${Math.ceil(s / 10) * 10}s`;
    }
    return `${s}s`;
  }

  updateAllUI() {
    const ratio = this.totalSec > 0 ? Math.max(0, Math.min(1, this.remainingSec / this.totalSec)) : 0;
    const palette = PALETTES[this.palette] || PALETTES.traffic;
    let stage = palette.high;
    if (this.remainingSec <= 0) stage = palette.done;
    else if (ratio <= 0.18) stage = palette.low;
    else if (ratio <= 0.45) stage = palette.mid;

    const currentTitle = this.getCurrentTitle();
    const currentIcon = this.getCurrentIcon();
    const shortTime = this.formatTimeForKid(this.remainingSec);

    const renderState = {
      totalSec: this.totalSec,
      remainingSec: this.remainingSec,
      isPaused: this.isPaused,
      visualStyle: this.visualStyle,
      numberMode: this.numberMode,
      palette: this.palette,
      step: this.step,
      currentTitle,
      currentIcon,
      formattedShortTime: shortTime,
      thenTitle: this.activeRoutine.thenTitle,
      thenIcon: this.activeRoutine.thenIcon
    };

    // 1. Render 512x512 Artwork on Car Media Tile & Phone Preview
    AutoArtworkRenderer.render(this.carCanvas, renderState);
    AutoArtworkRenderer.render(this.phoneCanvas, renderState);

    // 2. Update Car Coolwalk Media Tile Metadata (METADATA_KEY_TITLE / ARTIST)
    const titleEl = document.getElementById('carMediaTitle');
    const subEl = document.getElementById('carMediaSubtitle');

    if (this.remainingSec <= 0) {
      titleEl.textContent = `🎉 Arrived • ${currentTitle} ${currentIcon}`;
    } else if (this.remainingSec === this.totalSec && this.isPaused) {
      titleEl.textContent = `Ready • ${currentTitle} ${currentIcon}`;
    } else if (this.numberMode === 'none') {
      titleEl.textContent = `${stage.dot} ${currentTitle} ${currentIcon}`;
    } else {
      titleEl.textContent = `${stage.dot} ${shortTime} left • ${currentTitle} ${currentIcon}`;
    }

    if (this.remainingSec <= 0) {
      subEl.textContent = 'Routine Complete';
    } else if (this.remainingSec === this.totalSec && this.isPaused) {
      subEl.textContent = 'Tap Play to begin countdown';
    } else if (this.isPaused) {
      subEl.textContent = '⏸ Paused — Tap Play to resume';
    } else if (this.step === 'first' && this.activeRoutine.thenTitle) {
      subEl.textContent = `Next: ${this.activeRoutine.thenIcon || '✨'} ${this.activeRoutine.thenTitle} (${this.activeRoutine.thenMins}m)`;
    } else {
      subEl.textContent = `Step 2 of 2 • Doing great!`;
    }

    // Backdrop Tint
    const backdrop = document.getElementById('mediaTileBackdrop');
    if (backdrop) {
      backdrop.style.background = `radial-gradient(circle at 50% 35%, ${stage.main}, transparent 72%)`;
    }

    // Play/Pause Buttons
    const playIcon = this.isPaused ? '▶' : '⏸';
    const isAtStart = this.remainingSec === this.totalSec;
    const carPlayBtn = document.getElementById('carBtnPlayPause');
    if (carPlayBtn) carPlayBtn.textContent = playIcon;

    const phonePlayBtn = document.getElementById('phoneBtnPlayPause');
    if (phonePlayBtn) {
      phonePlayBtn.textContent = this.isPaused
        ? (isAtStart ? '▶ Start' : '▶ Resume')
        : '⏸ Pause';
    }

    // 3. Update Google Maps Card ETA Display
    const mapsMins = Math.max(1, Math.ceil(this.mapsSim.etaSeconds / 60));
    const etaEl = document.getElementById('mapsEtaMinutes');
    const badgeEl = document.getElementById('mapsTrafficBadge');
    etaEl.textContent = `${mapsMins} min`;

    if (this.mapsSim.trafficState === 'delay') {
      etaEl.style.color = '#fbbf24';
      badgeEl.textContent = '+3m Traffic delay';
      badgeEl.style.color = '#fbbf24';
      badgeEl.style.background = 'rgba(251, 191, 36, 0.16)';
    } else if (this.mapsSim.trafficState === 'faster') {
      etaEl.style.color = '#38bdf8';
      badgeEl.textContent = 'Faster route active';
      badgeEl.style.color = '#38bdf8';
      badgeEl.style.background = 'rgba(56, 189, 248, 0.16)';
    } else {
      etaEl.style.color = '#34d399';
      badgeEl.textContent = 'Smooth traffic';
      badgeEl.style.color = '#34d399';
      badgeEl.style.background = 'rgba(52, 211, 153, 0.15)';
    }

    const distMiles = (this.mapsSim.etaSeconds / 60 * 0.42).toFixed(1);
    document.getElementById('mapsDistanceRemaining').textContent = `${distMiles} mi`;

    // 4. Update Phone Companion Live Card
    document.getElementById('phoneStepPill').textContent =
      this.step === 'first' ? 'FIRST • NOW RUNNING' : 'THEN • STEP 2 ACTIVE';
    document.getElementById('phoneActiveTitle').textContent = `${currentIcon} ${currentTitle}`;

    const exactM = Math.floor(Math.ceil(this.remainingSec) / 60);
    const exactS = Math.ceil(this.remainingSec) % 60;
    document.getElementById('phoneTimeReadout').textContent =
      this.numberMode === 'exact'
        ? `${exactM}:${exactS < 10 ? '0' : ''}${exactS}`
        : shortTime;

    document.getElementById('phoneThenLabel').textContent =
      this.step === 'first' && this.activeRoutine.thenTitle
        ? `${this.activeRoutine.thenIcon} ${this.activeRoutine.thenTitle} (${this.activeRoutine.thenMins}m)`
        : 'Routine Complete 🎉';
  }

  renderRoutinesList() {
    const container = document.getElementById('phoneRoutinesList');
    container.innerHTML = '';

    this.routines.forEach((r) => {
      const item = document.createElement('div');
      const isActive = r.id === this.activeRoutine.id;
      const isPlaying = isActive && !this.isPaused;
      item.className = `routine-item ${isActive ? 'active' : ''}`;
      const thenText = r.thenTitle ? ` → Then: ${r.thenIcon} ${r.thenTitle} (${r.thenMins}m)` : '';
      const badgeLabel = isPlaying ? '⏸ Pause' : (isActive ? '▶ Start' : 'Load');
      item.innerHTML = `
        <div class="routine-main">
          <span class="routine-emoji">${r.firstIcon}</span>
          <div class="routine-text">
            <strong>${r.firstTitle} (${r.firstMins}m)</strong>
            <small>First: ${r.firstMins}m${thenText}</small>
          </div>
        </div>
        <div class="routine-right-actions">
          <button class="routine-icon-btn edit" title="Edit Routine">✏️</button>
          <button class="routine-icon-btn delete" title="Delete Routine">🗑️</button>
          <span class="routine-start-pill ${isPlaying ? 'playing' : ''}">${badgeLabel}</span>
        </div>
      `;

      item.querySelector('.routine-main').addEventListener('click', () => {
        if (isActive) {
          this.isPaused = !this.isPaused;
          this.updateAllUI();
          this.renderRoutinesList();
        } else {
          this.startRoutine(r, true, false);
          this.renderRoutinesList();
        }
      });
      item.querySelector('.routine-start-pill').addEventListener('click', (e) => {
        e.stopPropagation();
        if (isActive) {
          this.isPaused = !this.isPaused;
          this.updateAllUI();
          this.renderRoutinesList();
        } else {
          this.startRoutine(r, true, false);
          this.renderRoutinesList();
        }
      });

      item.querySelector('.routine-icon-btn.edit').addEventListener('click', (e) => {
        e.stopPropagation();
        this.openRoutineEditor(r);
      });

      item.querySelector('.routine-icon-btn.delete').addEventListener('click', (e) => {
        e.stopPropagation();
        this.deleteRoutine(r);
      });

      container.appendChild(item);
    });
  }

  openRoutineEditor(r) {
    const form = document.getElementById('routineCreatorForm');
    document.getElementById('editRoutineId').value = r ? r.id : '';
    document.getElementById('formHeaderTitle').textContent = r ? `Edit: ${r.firstTitle}` : 'New Routine';
    document.getElementById('inputFirstTitle').value = r ? r.firstTitle : '';
    document.getElementById('inputFirstIcon').value = r ? r.firstIcon : '🏡';
    document.getElementById('inputFirstMins').value = r ? r.firstMins : '15';
    document.getElementById('inputThenTitle').value = r && r.thenTitle ? r.thenTitle : '';
    document.getElementById('inputThenIcon').value = r && r.thenIcon ? r.thenIcon : '🍪';
    document.getElementById('inputThenMins').value = r && r.thenMins ? r.thenMins : '5';
    document.getElementById('btnSubmitRoutine').textContent = r ? 'Save Changes' : 'Save Routine';
    document.getElementById('btnCancelRoutine').classList.remove('hidden');
    form.classList.remove('hidden');
    form.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  deleteRoutine(r) {
    if (confirm(`Are you sure you want to delete "${r.firstTitle}"?`)) {
      this.routines = this.routines.filter((x) => x.id !== r.id);
      if (this.routines.length === 0) {
        this.routines = [...DEFAULT_ROUTINES];
      }
      this.saveRoutines();
      if (this.activeRoutine.id === r.id) {
        this.startRoutine(this.routines[0], false, false);
      }
      this.renderRoutinesList();
      this.renderDrawerContent();
      this.showCarToast('🗑️', 'Routine Deleted', `Removed "${r.firstTitle}"`);
    }
  }

  closeRoutineEditor() {
    const form = document.getElementById('routineCreatorForm');
    form.reset();
    document.getElementById('editRoutineId').value = '';
    document.getElementById('formHeaderTitle').textContent = 'New Routine';
    document.getElementById('btnSubmitRoutine').textContent = 'Save Routine';
    document.getElementById('btnCancelRoutine').classList.add('hidden');
    form.classList.add('hidden');
  }

  renderDrawerContent() {
    const container = document.getElementById('carDrawerContent');
    container.innerHTML = '';

    if (this.activeDrawerTab === 'routines') {
      this.routines.forEach((r) => {
        const btn = document.createElement('button');
        btn.className = 'car-browse-item';
        const thenText = r.thenTitle ? `Then: ${r.thenIcon} ${r.thenTitle}` : 'Single Timer';
        const isLoaded = r.id === this.activeRoutine.id;
        btn.innerHTML = `
          <div class="car-browse-item-left">
            <span class="car-browse-emoji">${r.firstIcon}</span>
            <div>
              <div class="car-browse-title">${r.firstTitle} (${r.firstMins} min)</div>
              <div class="car-browse-sub">${thenText}</div>
            </div>
          </div>
          <span class="car-browse-Select">${isLoaded ? '✓ Loaded' : 'Select'}</span>
        `;
        btn.addEventListener('click', () => {
          this.startRoutine(r, true, false);
          document.getElementById('carBrowseDrawer').classList.add('hidden');
          this.renderRoutinesList();
        });
        container.appendChild(btn);
      });
    } else if (this.activeDrawerTab === 'quick') {
      const grid = document.createElement('div');
      grid.className = 'car-quick-grid';
      [2, 5, 10, 15, 20, 30].forEach((mins) => {
        const qBtn = document.createElement('button');
        qBtn.className = 'car-quick-btn';
        qBtn.textContent = `${mins} min`;
        qBtn.addEventListener('click', () => {
          this.startQuickTimer(mins, false);
          document.getElementById('carBrowseDrawer').classList.add('hidden');
          this.renderRoutinesList();
        });
        grid.appendChild(qBtn);
      });
      container.appendChild(grid);
    } else if (this.activeDrawerTab === 'styles') {
      const styles = [
        { id: 'disc', icon: '🟢', title: 'Time Timer Shrinking Disc', sub: 'Classic disappearing color wedge' },
        { id: 'journey', icon: '🚗', title: 'Car Journey Progress Road', sub: 'Visual car travels from Start to Goal' },
        { id: 'blocks', icon: '🧩', title: 'Calm Segmented Blocks', sub: '10 discrete blocks, zero continuous motion' }
      ];
      styles.forEach((st) => {
        const btn = document.createElement('button');
        btn.className = 'car-browse-item';
        btn.innerHTML = `
          <div class="car-browse-item-left">
            <span class="car-browse-emoji">${st.icon}</span>
            <div>
              <div class="car-browse-title">${st.title}</div>
              <div class="car-browse-sub">${st.sub}</div>
            </div>
          </div>
          <span class="car-browse-Select">${this.visualStyle === st.id ? '✓ Active' : 'Select'}</span>
        `;
        btn.addEventListener('click', () => {
          this.setVisualStyle(st.id);
          document.getElementById('carBrowseDrawer').classList.add('hidden');
        });
        container.appendChild(btn);
      });
    }
  }

  setVisualStyle(styleId) {
    this.visualStyle = styleId;
    document.querySelectorAll('.style-card-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.style === styleId);
    });
    this.renderDrawerContent();
    this.updateAllUI();
  }

  bindEvents() {
    // Top Toolbar Simulation Speed
    document.querySelectorAll('[data-speed]').forEach((btn) => {
      btn.addEventListener('click', () => {
        this.simSpeed = Number(btn.dataset.speed);
        document.querySelectorAll('[data-speed]').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    // Top Toolbar Car Aspect Ratio
    document.querySelectorAll('[data-car-layout]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const isWide = btn.dataset.carLayout === 'widescreen';
        document.getElementById('coolwalkScreen').classList.toggle('widescreen', isWide);
        document.querySelectorAll('[data-car-layout]').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    // Top Toolbar View Mode
    document.querySelectorAll('[data-view-mode]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const carOnly = btn.dataset.viewMode === 'car-only';
        document.getElementById('simWorkspace').classList.toggle('car-only-mode', carOnly);
        document.querySelectorAll('[data-view-mode]').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    // Car Transport Controls (Streamlined 3 Controls)
    document.getElementById('carBtnReset').addEventListener('click', () => this.resetTimer());
    document.getElementById('carBtnPlayPause').addEventListener('click', () => {
      this.isPaused = !this.isPaused;
      this.updateAllUI();
      this.renderRoutinesList();
    });
    document.getElementById('carBtnPlus1').addEventListener('click', () => this.adjustTime(60));

    // Tap Car Artwork to cycle visual style quickly
    document.getElementById('mediaArtworkWrapper').addEventListener('click', () => {
      const order = ['disc', 'journey', 'blocks'];
      const next = order[(order.indexOf(this.visualStyle) + 1) % order.length];
      this.setVisualStyle(next);
    });

    // Car Media Browser Drawer Controls
    const drawer = document.getElementById('carBrowseDrawer');
    document.getElementById('btnOpenBrowseDrawer').addEventListener('click', () => {
      drawer.classList.remove('hidden');
      this.renderDrawerContent();
    });
    document.getElementById('railTimerBtn').addEventListener('click', () => {
      drawer.classList.toggle('hidden');
      this.renderDrawerContent();
    });
    document.getElementById('railLauncherBtn').addEventListener('click', () => {
      drawer.classList.toggle('hidden');
      this.renderDrawerContent();
    });
    document.getElementById('btnCloseBrowseDrawer').addEventListener('click', () => {
      drawer.classList.add('hidden');
    });

    document.querySelectorAll('[data-drawer-tab]').forEach((tabBtn) => {
      tabBtn.addEventListener('click', () => {
        this.activeDrawerTab = tabBtn.dataset.drawerTab;
        document.querySelectorAll('[data-drawer-tab]').forEach((b) => b.classList.remove('active'));
        tabBtn.classList.add('active');
        this.renderDrawerContent();
      });
    });

    // Rail Mic Button -> Spoken Status Update
    document.getElementById('railMicBtn').addEventListener('click', () => {
      const msg = `${this.formatTimeForKid(this.remainingSec)} left for ${this.getCurrentTitle()}.`;
      this.showCarToast('🎙️', 'Voice Status Update', msg);
      this.audio.speakCalmWarning(msg);
    });

    // Maps ETA Sync & Traffic Simulation
    const syncToggle = document.getElementById('mapsEtaSyncToggle');
    syncToggle.addEventListener('change', () => {
      this.mapsSyncEnabled = syncToggle.checked;
      document.getElementById('tileSyncBadge').classList.toggle('hidden', !this.mapsSyncEnabled);
      if (this.mapsSyncEnabled && this.step === 'first') {
        this.remainingSec = this.mapsSim.etaSeconds;
        this.showCarToast('🔗', 'Synced with Google Maps ETA', 'Timer now matches live navigation arrival');
      }
      this.updateAllUI();
    });

    document.getElementById('btnSimTrafficDelay').addEventListener('click', () => {
      this.simulateMapsTraffic(3, 'delay');
    });
    document.getElementById('btnSimTrafficFaster').addEventListener('click', () => {
      this.simulateMapsTraffic(-2, 'faster');
    });

    // Phone Controls (Streamlined 3 Controls)
    document.getElementById('phoneBtnReset').addEventListener('click', () => {
      this.resetTimer();
    });
    document.getElementById('phoneBtnPlayPause').addEventListener('click', () => {
      this.isPaused = !this.isPaused;
      this.updateAllUI();
      this.renderRoutinesList();
    });
    document.getElementById('phoneBtnPlus1').addEventListener('click', () => this.adjustTime(60));

    // Phone Visual Style Picker
    document.querySelectorAll('.style-card-btn').forEach((btn) => {
      btn.addEventListener('click', () => this.setVisualStyle(btn.dataset.style));
    });

    // Phone Number Mode Picker
    document.querySelectorAll('[data-num-mode]').forEach((btn) => {
      btn.addEventListener('click', () => {
        this.numberMode = btn.dataset.numMode;
        document.querySelectorAll('[data-num-mode]').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        this.updateAllUI();
      });
    });

    // Phone Color Palette Picker
    document.querySelectorAll('[data-palette]').forEach((btn) => {
      btn.addEventListener('click', () => {
        this.palette = btn.dataset.palette;
        document.querySelectorAll('[data-palette]').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        this.updateAllUI();
      });
    });

    // Audio & Voice Toggles + Test Buttons
    document.getElementById('toggleChimes').addEventListener('change', (e) => {
      this.chimesEnabled = e.target.checked;
    });
    document.getElementById('toggleVoice').addEventListener('change', (e) => {
      this.voiceEnabled = e.target.checked;
    });

    document.getElementById('btnTestChime').addEventListener('click', () => {
      this.audio.playGentleChime('milestone');
      this.showCarToast('🔔', 'Gentle Chime Preview', 'Soft harmonic triad (non-startling)');
    });
    document.getElementById('btnTestVoice').addEventListener('click', () => {
      const sample = `Five minutes left until ${this.getCurrentTitle()}. ${
        this.activeRoutine.thenTitle ? 'Then: ' + this.activeRoutine.thenTitle : ''
      }`;
      this.audio.speakCalmWarning(sample);
      this.showCarToast('🗣️', 'Spoken Transition Preview', sample);
    });

    // Custom Routine Creator & Editor Form
    const form = document.getElementById('routineCreatorForm');
    document.getElementById('btnToggleRoutineCreator').addEventListener('click', () => {
      if (form.classList.contains('hidden')) {
        this.openRoutineEditor(null);
      } else {
        this.closeRoutineEditor();
      }
    });

    document.getElementById('btnCancelRoutine').addEventListener('click', () => {
      this.closeRoutineEditor();
    });

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const editId = document.getElementById('editRoutineId').value;
      const firstTitle = document.getElementById('inputFirstTitle').value.trim();
      const firstIcon = document.getElementById('inputFirstIcon').value.trim() || '🚗';
      const firstMins = Math.max(1, parseInt(document.getElementById('inputFirstMins').value, 10) || 10);
      const thenTitle = document.getElementById('inputThenTitle').value.trim();
      const thenIcon = document.getElementById('inputThenIcon').value.trim() || '🌟';
      const thenMins = Math.max(0, parseInt(document.getElementById('inputThenMins').value, 10) || 0);

      if (!firstTitle) return;

      if (editId) {
        // Update existing routine
        const idx = this.routines.findIndex((x) => x.id === editId);
        if (idx !== -1) {
          const updated = {
            ...this.routines[idx],
            firstTitle,
            firstIcon,
            firstMins,
            thenTitle,
            thenIcon,
            thenMins,
            destStreet: firstTitle
          };
          this.routines[idx] = updated;
          this.saveRoutines();
          this.closeRoutineEditor();
          this.renderRoutinesList();
          this.renderDrawerContent();
          if (this.activeRoutine.id === editId) {
            this.startRoutine(updated, true, false);
          }
          this.showCarToast('✏️', 'Routine Updated', `Saved changes to "${firstTitle}"`);
        }
      } else {
        // Create new routine
        const newRoutine = {
          id: `custom-${Date.now()}`,
          firstTitle,
          firstIcon,
          firstMins,
          thenTitle,
          thenIcon,
          thenMins,
          destStreet: firstTitle
        };

        this.routines.unshift(newRoutine);
        this.saveRoutines();
        this.closeRoutineEditor();
        this.startRoutine(newRoutine, true, false);
        this.renderRoutinesList();
      }
    });
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.calmDriveApp = new VisualTimerApp();
});
