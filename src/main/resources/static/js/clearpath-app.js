/**
 * ClearPath — Emergency Traffic Clearance Client Application
 * All existing API endpoints, WebSocket topics, element IDs,
 * and functions preserved. UI behaviour enhanced.
 */

const AppState = {
  token: localStorage.getItem('clearpath_jwt'),
  officer: null,
  isOnDuty: false,
  coords: { lat: 12.9716, lon: 77.5946, heading: 0, speed: 0 },
  simulated: false,
  selectedCompass: 'N',
  map: null,
  officerMarker: null,
  ambulanceMarker: null,
  stompClient: null,
  pingInterval: null,
  activeSenderAlertId: null,
  currentIncomingAlert: null
};

// Preset locations for instant 2-device or multi-tab testing
const PRESETS = {
  origin: { lat: 12.9716, lon: 77.5946, label: 'MG Road Origin (Sender)' },
  ahead:  { lat: 12.9806, lon: 77.5946, label: '1 km North (Ahead Target)' },
  behind: { lat: 12.9626, lon: 77.5946, label: '1 km South (Behind — Filtered Out)' }
};

document.addEventListener('DOMContentLoaded', () => {
  initUIEvents();
  registerServiceWorker();

  if (AppState.token) {
    fetchProfile();
  } else {
    showScreen('loginScreen');
  }
});

function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js')
      .then(() => console.log('ClearPath Service Worker registered'))
      .catch(err => console.log('SW registration error:', err));
  }
}

function initUIEvents() {
  document.getElementById('loginBtn').addEventListener('click', handleLogin);
  document.getElementById('startDutyBtn').addEventListener('click', handleStartDuty);
  document.getElementById('endDutyBtn').addEventListener('click', handleEndDuty);
  document.getElementById('logoutBtn').addEventListener('click', handleLogout);
  document.getElementById('openTriggerModalBtn').addEventListener('click', openTriggerModal);
  document.getElementById('ackAlertBtn').addEventListener('click', handleAcknowledgeAlert);
  document.getElementById('dismissAlertBtn').addEventListener('click', handleDismissAlert);
  document.getElementById('ambulancePassedBtn').addEventListener('click', handleAmbulancePassed);

  // Hold-to-confirm dispatch
  initHoldToConfirm();

  // Dev toggle
  const devBtn = document.getElementById('devToggleBtn');
  if (devBtn) {
    devBtn.addEventListener('click', () => {
      const selector = document.getElementById('devSelector');
      const expanded = devBtn.getAttribute('aria-expanded') === 'true';
      devBtn.setAttribute('aria-expanded', String(!expanded));
      if (expanded) {
        selector.hidden = true;
      } else {
        selector.hidden = false;
      }
    });
  }

  // Enter key on login inputs
  ['officerIdInput', 'passwordInput'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('keydown', e => { if (e.key === 'Enter') handleLogin(); });
  });
}

/* ── Hold-to-confirm ───────────────────────────────────────── */
let holdTimer = null;
let holdStart = null;
const HOLD_DURATION = 1600; // ms

function initHoldToConfirm() {
  const btn = document.getElementById('confirmTriggerBtn');
  const circle = document.getElementById('holdProgressCircle');
  const label  = document.getElementById('holdBtnLabel');
  if (!btn) return;

  const circumference = 113; // 2π × 18

  function startHold(e) {
    e.preventDefault();
    if (btn.classList.contains('dispatching')) return;
    btn.classList.add('holding');
    holdStart = Date.now();

    function tick() {
      const elapsed = Date.now() - holdStart;
      const progress = Math.min(elapsed / HOLD_DURATION, 1);
      const offset = circumference * (1 - progress);
      if (circle) circle.style.strokeDashoffset = offset;
      if (label) label.textContent = progress < 1 ? 'Hold to dispatch' : 'Dispatching...';

      if (progress < 1) {
        holdTimer = requestAnimationFrame(tick);
      } else {
        finishHold();
      }
    }
    holdTimer = requestAnimationFrame(tick);
  }

  function cancelHold() {
    if (holdTimer) cancelAnimationFrame(holdTimer);
    holdTimer = null;
    holdStart = null;
    btn.classList.remove('holding');
    if (circle) circle.style.strokeDashoffset = circumference;
    if (label) label.textContent = 'Hold to dispatch';
  }

  function finishHold() {
    btn.classList.remove('holding');
    btn.classList.add('dispatching');
    handleTriggerAlert().finally(() => {
      btn.classList.remove('dispatching');
      if (circle) circle.style.strokeDashoffset = circumference;
      if (label) label.textContent = 'Hold to dispatch';
    });
  }

  btn.addEventListener('mousedown', startHold);
  btn.addEventListener('touchstart', startHold, { passive: false });
  btn.addEventListener('mouseup', e => { if (holdStart && (Date.now() - holdStart) < HOLD_DURATION) cancelHold(); });
  btn.addEventListener('mouseleave', cancelHold);
  btn.addEventListener('touchend', e => { if (holdStart && (Date.now() - holdStart) < HOLD_DURATION) cancelHold(); });
  btn.addEventListener('touchcancel', cancelHold);
}

/* ── Screen management ─────────────────────────────────────── */
function showScreen(screenId) {
  document.getElementById('loginScreen').style.display = screenId === 'loginScreen' ? 'flex' : 'none';
  document.getElementById('homeScreen').style.display  = screenId === 'homeScreen'  ? 'flex' : 'none';
  document.getElementById('dutyScreen').style.display  = screenId === 'dutyScreen'  ? 'flex' : 'none';

  if (screenId === 'dutyScreen' && AppState.map) {
    setTimeout(() => AppState.map.invalidateSize(), 200);
  }
}

function fillCredentials(id, label) {
  document.getElementById('officerIdInput').value = id;
  document.getElementById('passwordInput').value = 'password123';
  const errBox = document.getElementById('loginErrorMsg');
  errBox.textContent = `Profile selected: ${label}`;
  errBox.style.borderLeftColor = '#1354C4';
  errBox.style.background = '#E8F0FD';
  errBox.style.color = '#0D3E99';
  errBox.hidden = false;
}

/* ── Auth ──────────────────────────────────────────────────── */
async function handleLogin() {
  const officerId = document.getElementById('officerIdInput').value.trim();
  const password  = document.getElementById('passwordInput').value;
  const errBox    = document.getElementById('loginErrorMsg');

  if (!officerId || !password) {
    showLoginError('Officer ID and password are required.');
    return;
  }

  const loginBtn = document.getElementById('loginBtn');
  loginBtn.textContent = 'Verifying...';
  loginBtn.disabled = true;

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ officerId, password })
    });

    const data = await res.json();
    if (!res.ok) {
      showLoginError(data.message || 'Credentials not matched against police records. Try again.');
      return;
    }

    AppState.token = data.token;
    localStorage.setItem('clearpath_jwt', data.token);
    errBox.hidden = true;
    setOfficerProfile(data.profile);
  } catch (err) {
    showLoginError('Unable to connect to verification server. Check your connection.');
  } finally {
    loginBtn.textContent = 'Verify and enter';
    loginBtn.disabled = false;
  }
}

function showLoginError(msg) {
  const errBox = document.getElementById('loginErrorMsg');
  errBox.textContent = msg;
  errBox.style.borderLeftColor = '#C72020';
  errBox.style.background = '#FFF0F0';
  errBox.style.color = '#9A1313';
  errBox.hidden = false;
}

async function fetchProfile() {
  try {
    const res = await fetch('/api/me', {
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
    if (res.ok) {
      const profile = await res.json();
      setOfficerProfile(profile);
    } else {
      handleLogout();
    }
  } catch (e) {
    showScreen('loginScreen');
  }
}

function setOfficerProfile(profile) {
  AppState.officer = profile;
  AppState.isOnDuty = profile.onDuty;

  document.getElementById('profileName').textContent      = profile.name;
  document.getElementById('profileRank').textContent      = profile.rank;
  document.getElementById('profileBadge').textContent     = profile.badgeNumber;
  document.getElementById('profileOfficerId').textContent = profile.officerId;
  document.getElementById('profileStation').textContent   = `${profile.station} · ${profile.district}`;

  if (profile.photoUrl) {
    document.getElementById('profilePhoto').src = profile.photoUrl;
  } else {
    document.getElementById('profilePhoto').src = '/images/badge-avatar.svg';
  }

  updateHeaderStatus();

  if (AppState.isOnDuty) {
    showScreen('dutyScreen');
    initMap();
    connectWebSocket();
    startLocationTracking();
    checkActiveAlerts();
  } else {
    showScreen('homeScreen');
  }
}

function updateHeaderStatus() {
  // Update both header status badges (home and duty screens each have one)
  document.querySelectorAll('#headerStatusBadge').forEach(badge => {
    const text = badge.querySelector('#headerStatusText') || badge;
    const textEl = badge.querySelector('[id="headerStatusText"]') || badge.lastElementChild;

    if (AppState.isOnDuty) {
      badge.classList.add('on-duty');
      if (textEl) textEl.textContent = 'On duty';
    } else if (AppState.officer) {
      badge.classList.remove('on-duty');
      if (textEl) textEl.textContent = 'Off duty';
    } else {
      badge.classList.remove('on-duty');
      if (textEl) textEl.textContent = 'Offline';
    }
  });
}

/* ── Preset positions ──────────────────────────────────────── */
function setPresetPosition(key) {
  const preset = PRESETS[key];
  if (preset) {
    AppState.coords.lat = preset.lat;
    AppState.coords.lon = preset.lon;
    AppState.simulated = true;
    alert(`Testing location set to: ${preset.label} (${preset.lat}, ${preset.lon})`);
    if (AppState.map && AppState.officerMarker) {
      AppState.officerMarker.setLatLng([preset.lat, preset.lon]);
      AppState.map.setView([preset.lat, preset.lon], 16);
    }
  }
}

/* ── Duty management ───────────────────────────────────────── */
async function handleStartDuty() {
  try {
    if (!AppState.simulated && 'geolocation' in navigator) {
      try {
        const pos = await new Promise((resolve, reject) => {
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            enableHighAccuracy: true,
            timeout: 5000
          });
        });
        AppState.coords.lat     = pos.coords.latitude;
        AppState.coords.lon     = pos.coords.longitude;
        AppState.coords.heading = pos.coords.heading || 0;
        AppState.coords.speed   = pos.coords.speed   || 0;
      } catch (locErr) {
        console.warn('GPS request timed out or denied, using central outpost coordinates');
      }
    }

    const res = await fetch('/api/duty/start', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });

    if (!res.ok) throw new Error('Duty start failed');

    AppState.isOnDuty = true;
    updateHeaderStatus();
    showScreen('dutyScreen');
    initMap();
    connectWebSocket();
    startLocationTracking();
    checkActiveAlerts();
  } catch (e) {
    document.getElementById('permissionDeniedNotice').hidden = false;
  }
}

async function handleEndDuty() {
  if (!confirm('End current traffic duty shift and stop live location sharing?')) return;

  try {
    await fetch('/api/duty/end', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
  } catch (e) {}

  AppState.isOnDuty = false;
  if (AppState.pingInterval) { clearInterval(AppState.pingInterval); AppState.pingInterval = null; }
  if (AppState.stompClient)  { AppState.stompClient.disconnect(); }
  updateHeaderStatus();
  showScreen('homeScreen');
}

function handleLogout() {
  localStorage.removeItem('clearpath_jwt');
  AppState.token    = null;
  AppState.officer  = null;
  AppState.isOnDuty = false;
  if (AppState.pingInterval) clearInterval(AppState.pingInterval);
  if (AppState.stompClient)  AppState.stompClient.disconnect();
  updateHeaderStatus();
  showScreen('loginScreen');
}

/* ── Map ───────────────────────────────────────────────────── */
function initMap() {
  if (AppState.map) {
    AppState.map.invalidateSize();
    return;
  }

  const mapContainer = document.getElementById('leafletMap');
  AppState.map = L.map(mapContainer, {
    zoomControl: false,
    attributionControl: false
  }).setView([AppState.coords.lat, AppState.coords.lon], 16);

  L.control.zoom({ position: 'bottomright' }).addTo(AppState.map);

  // Light tile layer to match the UI theme
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19
  }).addTo(AppState.map);

  const officerIcon = L.divIcon({
    className: 'officer-map-pin',
    html: `<div style="background:#1354C4;border:2.5px solid #fff;width:18px;height:18px;border-radius:4px;box-shadow:0 2px 6px rgba(19,84,196,0.45);"></div>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9]
  });

  AppState.officerMarker = L.marker([AppState.coords.lat, AppState.coords.lon], { icon: officerIcon })
    .addTo(AppState.map)
    .bindPopup(`<b>${AppState.officer.rank} ${AppState.officer.name}</b><br>ID: ${AppState.officer.officerId}`);

  updateCoordsDisplay();
}

function updateCoordsDisplay() {
  const el = document.getElementById('currentCoords');
  if (el) el.textContent = `Lat: ${AppState.coords.lat.toFixed(4)}, Lon: ${AppState.coords.lon.toFixed(4)}`;
}

function startLocationTracking() {
  transmitLocation();

  if (!AppState.pingInterval) {
    AppState.pingInterval = setInterval(transmitLocation, 6000);
  }

  if (!AppState.simulated && 'geolocation' in navigator) {
    navigator.geolocation.watchPosition(pos => {
      if (!AppState.simulated) {
        AppState.coords.lat     = pos.coords.latitude;
        AppState.coords.lon     = pos.coords.longitude;
        AppState.coords.heading = pos.coords.heading || 0;
        AppState.coords.speed   = pos.coords.speed   || 0;
        if (AppState.officerMarker) {
          AppState.officerMarker.setLatLng([AppState.coords.lat, AppState.coords.lon]);
        }
        updateCoordsDisplay();
      }
    }, err => console.log('WatchPosition notice:', err.message), {
      enableHighAccuracy: true,
      maximumAge: 5000
    });
  }
}

async function transmitLocation() {
  if (!AppState.isOnDuty || !AppState.token) return;
  try {
    const res = await fetch('/api/location', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${AppState.token}`
      },
      body: JSON.stringify({
        latitude:  AppState.coords.lat,
        longitude: AppState.coords.lon,
        heading:   AppState.coords.heading,
        speed:     AppState.coords.speed
      })
    });
    if (res.ok) {
      const el = document.getElementById('currentRoadName');
      if (el) el.textContent = 'Patrol post (GPS active)';
    }
  } catch (e) {
    console.debug('Telemetry ping skipped:', e);
  }
}

/* ── WebSocket ─────────────────────────────────────────────── */
function connectWebSocket() {
  if (AppState.stompClient && AppState.stompClient.connected) return;

  const socket = new SockJS('/ws');
  AppState.stompClient = Stomp.over(socket);
  AppState.stompClient.debug = null;

  AppState.stompClient.connect({
    'Authorization': `Bearer ${AppState.token}`,
    'token': AppState.token
  }, frame => {
    console.log('Connected to ClearPath STOMP Dispatch');

    AppState.stompClient.subscribe('/user/queue/alerts', message => {
      const alertData = JSON.parse(message.body);
      handleIncomingEmergencyAlert(alertData);
    });

    if (AppState.activeSenderAlertId) {
      subscribeToAlertUpdates(AppState.activeSenderAlertId);
    }
  }, error => {
    console.warn('STOMP reconnecting in 5s...', error);
    setTimeout(connectWebSocket, 5000);
  });
}

function subscribeToAlertUpdates(alertId) {
  if (!AppState.stompClient || !AppState.stompClient.connected) return;

  AppState.stompClient.subscribe(`/topic/alerts/${alertId}`, message => {
    const data = JSON.parse(message.body);
    if (data.status === 'PASSED' || data.status === 'EXPIRED') {
      document.getElementById('senderActiveStatus').style.display = 'none';
      AppState.activeSenderAlertId = null;
      if (AppState.ambulanceMarker) {
        AppState.map.removeLayer(AppState.ambulanceMarker);
        AppState.ambulanceMarker = null;
      }
      alert('Ambulance alert cleared. Traffic flow normal.');
    } else {
      const el = document.getElementById('senderAckCounter');
      if (el) el.textContent = `${data.acknowledgedCount} / ${data.notifiedCount} acked`;
    }
  });
}

/* ── Trigger modal ─────────────────────────────────────────── */
function openTriggerModal() {
  document.getElementById('triggerModal').className = 'modal-overlay active';
}

function closeTriggerModal() {
  document.getElementById('triggerModal').className = 'modal-overlay';
}

function selectCompass(dir) {
  AppState.selectedCompass = dir;
  document.querySelectorAll('.compass-btn').forEach(btn => {
    btn.classList.toggle('selected', btn.getAttribute('data-dir') === dir);
  });

  // Rotate needle indicator if present
  const needle = document.getElementById('compassNeedle');
  if (needle) {
    const deg = {N:0,NE:45,E:90,SE:135,S:180,SW:225,W:270,NW:315}[dir] || 0;
    needle.style.transform = `rotate(${deg}deg)`;
  }
}

async function handleTriggerAlert() {
  const confirmBtn = document.getElementById('confirmTriggerBtn');
  const type  = document.getElementById('ambulanceTypeSelect').value;
  const plate = document.getElementById('ambulancePlateInput').value.trim();

  try {
    const res = await fetch('/api/alerts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${AppState.token}`
      },
      body: JSON.stringify({
        latitude:         AppState.coords.lat,
        longitude:        AppState.coords.lon,
        compassDirection: AppState.selectedCompass,
        ambulanceType:    type,
        ambulancePlate:   plate
      })
    });

    const data = await res.json();
    if (!res.ok) {
      alert(data.message || 'Trigger rejected.');
      return;
    }

    closeTriggerModal();
    AppState.activeSenderAlertId = data.alertId;

    // Show sender status bar
    const roadEl = document.getElementById('senderRoadLabel');
    const ackEl  = document.getElementById('senderAckCounter');
    const roadsEl = document.getElementById('senderConnectingRoads');
    if (roadEl)  roadEl.textContent  = `${data.roadName || 'Main Corridor'} (facing ${data.compassDirection})`;
    if (ackEl)   ackEl.textContent   = `0 / ${data.notifiedCount} acked`;
    if (roadsEl) roadsEl.textContent = data.connectingRoads && data.connectingRoads.length > 0
      ? data.connectingRoads.join(', ') : 'Arterial junctions';
    document.getElementById('senderActiveStatus').style.display = 'block';

    subscribeToAlertUpdates(data.alertId);

    // Plot ambulance on map
    if (AppState.map) {
      if (AppState.ambulanceMarker) AppState.map.removeLayer(AppState.ambulanceMarker);
      const ambIcon = L.divIcon({
        className: 'amb-map-pin',
        html: `<div style="background:#C72020;border:2px solid #fff;width:24px;height:24px;border-radius:4px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:12px;box-shadow:0 2px 8px rgba(199,32,32,0.55);">A</div>`,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });
      AppState.ambulanceMarker = L.marker([AppState.coords.lat, AppState.coords.lon], { icon: ambIcon })
        .addTo(AppState.map)
        .bindPopup(`<b>Ambulance dispatched (${data.compassDirection})</b><br>${data.notifiedCount} officers alerted`)
        .openPopup();
    }

    // Count-up animation for notified count
    animateCountUp(data.notifiedCount);

    alert(`Alert dispatched. ${data.notifiedCount} officers within 3 km ahead have been notified.`);
  } catch (err) {
    alert('Failed to trigger alert. Check connection.');
  }
}

function animateCountUp(target) {
  const el = document.getElementById('senderAckCounter');
  if (!el) return;
  let current = 0;
  const step = Math.max(1, Math.floor(target / 20));
  const timer = setInterval(() => {
    current = Math.min(current + step, target);
    el.textContent = `0 / ${current} acked`;
    if (current >= target) clearInterval(timer);
  }, 40);
}

/* ── Incoming alert ────────────────────────────────────────── */
function handleIncomingEmergencyAlert(alert) {
  AppState.currentIncomingAlert = alert;

  window.ambulanceSiren.start();

  const senderEl  = document.getElementById('alertSenderName');
  const badgeEl   = document.getElementById('alertSenderBadge');
  const roadEl    = document.getElementById('alertRoadName');
  const distEl    = document.getElementById('alertDistanceVal');
  const headingEl = document.getElementById('alertHeadingVal');

  if (senderEl)  senderEl.textContent  = `${alert.sender.rank} ${alert.sender.name}`;
  if (badgeEl)   badgeEl.textContent   = `${alert.sender.badgeNumber} — ${alert.sender.station}`;
  if (roadEl)    roadEl.textContent    = alert.roadName || 'Main Road Corridor';
  if (distEl)    distEl.textContent    = `${alert.distanceKm.toFixed(2)} km`;
  if (headingEl) headingEl.textContent = `Facing ${alert.compassDirection} (${alert.headingDeg.toFixed(0)}°)`;

  const roadList = document.getElementById('alertConnectingRoadsList');
  roadList.innerHTML = '';
  if (alert.connectingRoads && alert.connectingRoads.length > 0) {
    alert.connectingRoads.forEach(r => {
      const span = document.createElement('span');
      span.className = 'connecting-road-badge';
      span.textContent = r;
      roadList.appendChild(span);
    });
  } else {
    roadList.innerHTML = '<span class="connecting-road-badge">Forward junctions</span>';
  }

  // Vibrate if supported
  if ('vibrate' in navigator) {
    navigator.vibrate([300, 100, 300, 100, 500]);
  }

  document.getElementById('sirenAlertOverlay').className = 'siren-alert-container active';

  // Animate the distance number counting down
  animateDistanceCountIn(alert.distanceKm);
}

function animateDistanceCountIn(targetKm) {
  const el = document.getElementById('alertDistanceVal');
  if (!el) return;
  let start = targetKm * 1.15;
  const startTime = Date.now();
  const duration = 1200;
  function tick() {
    const t = Math.min((Date.now() - startTime) / duration, 1);
    const ease = 1 - Math.pow(1 - t, 3);
    const val = start + (targetKm - start) * ease;
    el.textContent = `${val.toFixed(2)} km`;
    if (t < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

async function handleAcknowledgeAlert() {
  if (!AppState.currentIncomingAlert) return;

  const alertId = AppState.currentIncomingAlert.alertId;
  window.ambulanceSiren.stop();
  document.getElementById('sirenAlertOverlay').className = 'siren-alert-container';

  try {
    await fetch(`/api/alerts/${alertId}/ack`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
    alert('Acknowledgment recorded. You are clearing the route.');
  } catch (e) {
    console.error('Ack error:', e);
  }
}

async function handleDismissAlert() {
  if (!AppState.currentIncomingAlert) return;

  const alertId = AppState.currentIncomingAlert.alertId;
  window.ambulanceSiren.stop();
  document.getElementById('sirenAlertOverlay').className = 'siren-alert-container';

  try {
    await fetch(`/api/alerts/${alertId}/dismiss`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
  } catch (e) {
    console.error('Dismiss error:', e);
  }
}

async function handleAmbulancePassed() {
  if (!AppState.activeSenderAlertId) return;

  try {
    await fetch(`/api/alerts/${AppState.activeSenderAlertId}/complete`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
    document.getElementById('senderActiveStatus').style.display = 'none';
    AppState.activeSenderAlertId = null;
    if (AppState.ambulanceMarker) {
      AppState.map.removeLayer(AppState.ambulanceMarker);
      AppState.ambulanceMarker = null;
    }
    alert('Ambulance marked as passed. Route cleared.');
  } catch (e) {
    alert('Failed to update status.');
  }
}

async function checkActiveAlerts() {
  if (!AppState.token) return;
  try {
    const res = await fetch('/api/alerts/active', {
      headers: { 'Authorization': `Bearer ${AppState.token}` }
    });
    if (res.ok) {
      const list = await res.json();
      if (list.length > 0) {
        const first = list[0];
        if (first.sender.id === AppState.officer.id) {
          AppState.activeSenderAlertId = first.alertId;
          const roadEl = document.getElementById('senderRoadLabel');
          const ackEl  = document.getElementById('senderAckCounter');
          if (roadEl) roadEl.textContent = `${first.roadName || 'Corridor'} (facing ${first.compassDirection})`;
          if (ackEl)  ackEl.textContent  = `${first.acknowledgedCount} / ${first.notifiedCount} acked`;
          document.getElementById('senderActiveStatus').style.display = 'block';
          subscribeToAlertUpdates(first.alertId);
        }
      }
    }
  } catch (e) {}
}
