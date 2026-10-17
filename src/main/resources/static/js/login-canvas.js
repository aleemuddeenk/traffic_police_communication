/**
 * ClearPath — Login screen animated map canvas
 * Draws a live ambulance dot moving along a road with
 * a forward-facing cone and radar rings sweeping ahead.
 * Illustrates the "3 km forward-only" dispatch concept.
 */
(function () {
  const canvas = document.getElementById('loginMapCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  let W, H, raf;

  // State
  const amb = { x: 0, y: 0, t: 0, speed: 0.0012 };
  let rings  = [];
  let pingTimer = 0;

  // Road waypoints (normalised 0–1)
  const road = [
    { x: 0.1,  y: 0.80 },
    { x: 0.38, y: 0.62 },
    { x: 0.58, y: 0.50 },
    { x: 0.80, y: 0.38 },
    { x: 0.95, y: 0.28 }
  ];

  // Officer dots — stationary ahead of ambulance
  const officers = [
    { nx: 0.68, ny: 0.42, active: false },
    { nx: 0.82, ny: 0.34, active: false }
  ];

  function resize() {
    W = canvas.width  = canvas.offsetWidth;
    H = canvas.height = canvas.offsetHeight;
  }

  function lerp(a, b, t) { return a + (b - a) * t; }

  // Interpolate along road polyline (t=0..1)
  function posOnRoad(t) {
    const segments = road.length - 1;
    const seg = Math.min(Math.floor(t * segments), segments - 1);
    const local = (t * segments) - seg;
    const p0 = road[seg];
    const p1 = road[seg + 1];
    return {
      x: lerp(p0.x, p1.x, local) * W,
      y: lerp(p0.y, p1.y, local) * H
    };
  }

  function headingAngle(t) {
    const dt = 0.01;
    const a  = posOnRoad(Math.max(0, t - dt));
    const b  = posOnRoad(Math.min(1, t + dt));
    return Math.atan2(b.y - a.y, b.x - a.x);
  }

  function addRing() {
    rings.push({ x: amb.x, y: amb.y, r: 0, maxR: Math.min(W, H) * 0.42, opacity: 0.7 });
  }

  function drawGrid(alpha) {
    ctx.save();
    ctx.strokeStyle = `rgba(100,160,255,${alpha})`;
    ctx.lineWidth = 0.5;
    const step = Math.min(W, H) / 7;
    for (let x = 0; x < W; x += step) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = 0; y < H; y += step) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.restore();
  }

  function drawRoads() {
    ctx.save();
    ctx.strokeStyle = 'rgba(120,170,255,0.35)';
    ctx.lineWidth   = 3;
    ctx.lineJoin    = 'round';
    ctx.lineCap     = 'round';
    ctx.beginPath();
    road.forEach((p, i) => {
      if (i === 0) ctx.moveTo(p.x * W, p.y * H);
      else ctx.lineTo(p.x * W, p.y * H);
    });
    ctx.stroke();
    // Side streets
    ctx.strokeStyle = 'rgba(100,150,220,0.18)';
    ctx.lineWidth   = 1.5;
    [[0.38, 0.62, 0.42, 0.95], [0.58, 0.50, 0.62, 0.85], [0.80, 0.38, 0.84, 0.70]].forEach(s => {
      ctx.beginPath();
      ctx.moveTo(s[0] * W, s[1] * H);
      ctx.lineTo(s[2] * W, s[3] * H);
      ctx.stroke();
    });
    ctx.restore();
  }

  function drawCone(x, y, angle) {
    const spread = Math.PI / 3; // 60 degrees total
    const length = Math.min(W, H) * 0.38;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.arc(x, y, length, angle - spread / 2, angle + spread / 2);
    ctx.closePath();
    const grad = ctx.createRadialGradient(x, y, 0, x, y, length);
    grad.addColorStop(0,   'rgba(255,230,60,0.18)');
    grad.addColorStop(0.5, 'rgba(255,220,30,0.10)');
    grad.addColorStop(1,   'rgba(255,200, 0,0)');
    ctx.fillStyle = grad;
    ctx.fill();
    ctx.restore();
  }

  function drawRings() {
    rings = rings.filter(r => r.opacity > 0.01);
    rings.forEach(r => {
      ctx.beginPath();
      ctx.arc(r.x, r.y, r.r, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(255,200,50,${r.opacity})`;
      ctx.lineWidth = 1.2;
      ctx.stroke();
      r.r       += 2.5;
      r.opacity *= 0.972;
    });
  }

  function drawOfficers(ambX, ambY, angle) {
    officers.forEach(off => {
      const ox = off.nx * W;
      const oy = off.ny * H;
      // Is this officer in the forward cone?
      const dx = ox - ambX;
      const dy = oy - ambY;
      const toOff = Math.atan2(dy, dx);
      let diff = toOff - angle;
      while (diff >  Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      off.active = Math.abs(diff) < Math.PI / 3;

      // Draw officer dot
      ctx.beginPath();
      ctx.arc(ox, oy, 5, 0, Math.PI * 2);
      ctx.fillStyle = off.active ? 'rgba(90,220,150,0.95)' : 'rgba(180,200,240,0.55)';
      ctx.fill();

      if (off.active) {
        // Ripple
        ctx.beginPath();
        ctx.arc(ox, oy, 10 + Math.sin(Date.now() / 250) * 4, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(90,220,150,0.35)';
        ctx.lineWidth = 1.5;
        ctx.stroke();
      }
    });
  }

  function drawAmb(x, y) {
    // Red ambulance dot
    ctx.beginPath();
    ctx.arc(x, y, 7, 0, Math.PI * 2);
    ctx.fillStyle = '#FF4444';
    ctx.fill();
    // White border
    ctx.beginPath();
    ctx.arc(x, y, 8.5, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255,255,255,0.8)';
    ctx.lineWidth = 2;
    ctx.stroke();
    // Inner cross
    ctx.save();
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1.8;
    ctx.beginPath();
    ctx.moveTo(x - 3, y); ctx.lineTo(x + 3, y);
    ctx.moveTo(x, y - 3); ctx.lineTo(x, y + 3);
    ctx.stroke();
    ctx.restore();
  }

  function draw(ts) {
    if (!ctx) return;
    ctx.clearRect(0, 0, W, H);

    // Background
    ctx.fillStyle = '#071830';
    ctx.fillRect(0, 0, W, H);

    drawGrid(0.07);
    drawRoads();

    // Advance ambulance
    amb.t = (amb.t + amb.speed) % 1;
    const pos   = posOnRoad(amb.t);
    const angle = headingAngle(amb.t);
    amb.x = pos.x;
    amb.y = pos.y;

    // Periodic radar ping
    pingTimer++;
    if (pingTimer % 90 === 0) addRing();

    drawCone(amb.x, amb.y, angle);
    drawRings();
    drawOfficers(amb.x, amb.y, angle);
    drawAmb(amb.x, amb.y);

    raf = requestAnimationFrame(draw);
  }

  function init() {
    resize();
    addRing();
    raf = requestAnimationFrame(draw);
  }

  window.addEventListener('resize', () => {
    resize();
  });

  // Start only when login screen is visible
  init();
})();
