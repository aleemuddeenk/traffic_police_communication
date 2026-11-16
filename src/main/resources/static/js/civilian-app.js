/* ═══════════════════════════════════════════════════════════════
   ClearPath — Civilian App (V2V)
   Handles: role chooser, civilian OTP login, civilian dashboard,
            WebSocket alert reception, live map, location sharing.
   All police logic lives in clearpath-app.js (untouched).
═══════════════════════════════════════════════════════════════ */

'use strict';

/* ──────────────────────────────────────────────────────────────
   STATE
─────────────────────────────────────────────────────────────── */
let civToken       = null;
let civProfile     = null;
let civStompClient = null;
let civLocInterval = null;
let civAlertMap    = null;
let civAmbMarker   = null;
let civMeMarker    = null;
let civMyLat       = null;
let civMyLon       = null;
let civActiveAlert = null;    // current alert payload

/* ──────────────────────────────────────────────────────────────
   SCREEN NAVIGATION  (global — called from onclick / police code)
─────────────────────────────────────────────────────────────── */
function showRoleChooser() {
  hideAllScreens();
  show('roleChooserScreen');
  // Kick off the canvas animation on the chooser
  if (window.startLoginCanvas) window.startLoginCanvas('loginMapCanvas');
}

function showPoliceLogin() {
  hideAllScreens();
  show('loginScreen');
  // The police canvas was removed from loginScreen pane — no canvas needed here
}

function showCivilianLogin() {
  hideAllScreens();
  show('civilianLoginScreen');
  if (window.startLoginCanvas) window.startLoginCanvas('civLoginMapCanvas');
}

function hideAllScreens() {
  const ids = [
    'roleChooserScreen','loginScreen','homeScreen',
    'dutyScreen','sirenAlertOverlay',
    'civilianLoginScreen','civilianDashScreen'
  ];
  ids.forEach(id => { const el = document.getElementById(id); if (el) el.style.display = 'none'; });
}

function show(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = '';
}

/* ──────────────────────────────────────────────────────────────
   BOOT — decide first screen
─────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  const savedToken = localStorage.getItem('clearpath_token');
  const savedRole  = localStorage.getItem('clearpath_role');

  if (savedToken && savedRole === 'CIVILIAN') {
    civToken   = savedToken;
    civProfile = JSON.parse(localStorage.getItem('clearpath_civ_profile') || '{}');
    launchCivilianDash();
    return;
  }

  if (savedToken && savedRole === 'POLICE') {
    // Police flow handled by clearpath-app.js — it reads the token itself
    // Just make sure we don't show the chooser for returning police officers
    return; // clearpath-app.js DOMContentLoaded fires too and handles this
  }

  showRoleChooser();
});

/* ──────────────────────────────────────────────────────────────
   OTP FLOW
─────────────────────────────────────────────────────────────── */
document.getElementById('civSendOtpBtn')?.addEventListener('click', async () => {
  const phone = document.getElementById('civPhoneInput').value.trim();
  if (!phone) { showCivError('Please enter your mobile number.'); return; }

  setBtn('civSendOtpBtn', 'Sending…', true);
  hideCivError();

  try {
    const res = await fetch('/api/auth/civilian/send-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber: phone })
    });
    const data = await res.json();

    if (!res.ok) {
      showCivError(data.message || 'Could not send OTP. Please try again.');
      setBtn('civSendOtpBtn', 'Send OTP', false);
      return;
    }

    // Show OTP step
    document.getElementById('civOtpPhoneDisplay').textContent = phone;
    document.getElementById('civPhoneStep').style.display = 'none';
    document.getElementById('civOtpStep').style.display = '';

    // Show demo OTP (remove in production)
    if (data.demoOtp) {
      document.getElementById('civDemoOtpBox').style.display = '';
      document.getElementById('civDemoOtpVal').textContent = data.demoOtp;
    }

    setBtn('civSendOtpBtn', 'Send OTP', false);
  } catch (e) {
    showCivError('Network error. Please check your connection.');
    setBtn('civSendOtpBtn', 'Send OTP', false);
  }
});

document.getElementById('civVerifyOtpBtn')?.addEventListener('click', async () => {
  const phone = document.getElementById('civPhoneInput').value.trim();
  const otp   = document.getElementById('civOtpInput').value.trim();

  if (!otp || otp.length < 4) { showCivError('Please enter the OTP.'); return; }

  setBtn('civVerifyOtpBtn', 'Verifying…', true);
  hideCivError();

  try {
    const res = await fetch('/api/auth/civilian/verify-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber: phone, otp })
    });
    const data = await res.json();

    if (!res.ok) {
      showCivError(data.message || 'Invalid OTP. Please try again.');
      setBtn('civVerifyOtpBtn', 'Verify and enter', false);
      return;
    }

    civToken   = data.token;
    civProfile = data.profile;
    localStorage.setItem('clearpath_token',       civToken);
    localStorage.setItem('clearpath_role',        'CIVILIAN');
    localStorage.setItem('clearpath_civ_phone',   phone);
    localStorage.setItem('clearpath_civ_profile', JSON.stringify(civProfile));

    setBtn('civVerifyOtpBtn', 'Verify and enter', false);
    launchCivilianDash();
  } catch (e) {
    showCivError('Network error. Please check your connection.');
    setBtn('civVerifyOtpBtn', 'Verify and enter', false);
  }
});

function civGoBackToPhone() {
  document.getElementById('civOtpStep').style.display = 'none';
  document.getElementById('civPhoneStep').style.display = '';
  document.getElementById('civDemoOtpBox').style.display = 'none';
  hideCivError();
}

/* ──────────────────────────────────────────────────────────────
   CIVILIAN DASHBOARD
─────────────────────────────────────────────────────────────── */
function launchCivilianDash() {
  hideAllScreens();
  show('civilianDashScreen');
  updateCivStatus('Connecting…', false);

  // Connect WebSocket
  connectCivilianWs();

  // Start location sharing
  startCivilianLocation();

  // Logout handlers
  const topLogout = document.getElementById('civLogoutBtn');
  if (topLogout) topLogout.onclick = civilianLogout;
  const bottomLogout = document.getElementById('civBottomLogoutBtn');
  if (bottomLogout) bottomLogout.onclick = civilianLogout;
  const centerLogout = document.getElementById('civCenterLogoutBtn');
  if (centerLogout) centerLogout.onclick = civilianLogout;
  document.getElementById('civDismissAlertBtn').onclick = dismissCivAlert;

  // Display user phone
  const phone = (civProfile && (civProfile.phoneNumber || civProfile.phone)) || localStorage.getItem('clearpath_civ_phone') || 'Active Civilian';
  const phoneEl = document.getElementById('civUserPhoneDisplay');
  if (phoneEl) phoneEl.textContent = phone;
}

function updateCivStatus(text, online) {
  const chip = document.getElementById('civStatusChip');
  const dot  = chip?.querySelector('.status-dot');
  const txt  = document.getElementById('civStatusText');
  if (txt) txt.textContent = text;
  if (dot) {
    dot.style.background = online ? '#16a34a' : '#94a3b8';
  }
  if (chip) {
    chip.classList.remove('status-chip-duty');
    chip.classList.toggle('status-chip-live', online);
  }
}

/* ── WebSocket ──────────────────────────────────────────────── */
function connectCivilianWs() {
  if (!window.SockJS || !window.Stomp) return;

  const sockUrl = window.location.origin + '/ws';
  const sock    = new SockJS(sockUrl);
  civStompClient = Stomp.over(sock);
  civStompClient.debug = () => {};

  civStompClient.connect(
    { Authorization: 'Bearer ' + civToken },
    () => {
      updateCivStatus('Live', true);
      // Subscribe to civilian-specific alert queue
      civStompClient.subscribe('/user/queue/alerts', onCivilianAlert);
    },
    (err) => {
      updateCivStatus('Reconnecting…', false);
      setTimeout(connectCivilianWs, 5000);
    }
  );
}

function onCivilianAlert(frame) {
  try {
    const payload = JSON.parse(frame.body);
    if (payload.type === 'AMBULANCE_APPROACHING') {
      showCivAlert(payload);
    } else if (payload.status === 'EXPIRED' || payload.status === 'PASSED') {
      dismissCivAlert();
    }
  } catch (e) { /* ignore */ }
}

/* ── Alert display ──────────────────────────────────────────── */
function showCivAlert(payload) {
  civActiveAlert = payload;

  // Switch panels
  document.getElementById('civSafeState').style.display   = 'none';
  document.getElementById('civAlertState').style.display  = '';

  // Distance
  const metres = payload.distance || Math.round((payload.distanceKm || 0) * 1000);
  document.getElementById('civDistanceNum').textContent =
    metres >= 1000 ? (metres / 1000).toFixed(1) + ' km' : metres + ' m';

  // Direction (from, not to)
  const fromDir = oppositeDirection(payload.direction);
  document.getElementById('civAlertDirection').textContent = 'From ' + fromDir;
  document.getElementById('civAlertRoad').textContent = payload.roadName || '—';
  document.getElementById('civAlertStatus').textContent = 'Approaching';

  // Map
  initCivAlertMap(payload.ambulanceLat, payload.ambulanceLon);

  // Audio siren
  if (window.playSiren) window.playSiren();

  // Vibrate (mobile)
  if (navigator.vibrate) navigator.vibrate([400, 200, 400, 200, 400]);
}

function dismissCivAlert() {
  civActiveAlert = null;
  document.getElementById('civAlertState').style.display  = 'none';
  document.getElementById('civSafeState').style.display   = '';
  if (window.stopSiren) window.stopSiren();
}

function initCivAlertMap(ambLat, ambLon) {
  const mapEl = document.getElementById('civAlertMap');
  if (!mapEl || !window.L) return;

  const centerLat = civMyLat || ambLat;
  const centerLon = civMyLon || ambLon;

  if (!civAlertMap) {
    civAlertMap = L.map('civAlertMap', { zoomControl: false, attributionControl: false });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(civAlertMap);
  }

  civAlertMap.setView([centerLat, centerLon], 15);

  // Ambulance marker (red)
  if (civAmbMarker) civAmbMarker.remove();
  const ambIcon = L.divIcon({
    html: '<div style="background:#C72020;width:16px;height:16px;border-radius:50%;border:3px solid #fff;box-shadow:0 0 8px rgba(199,32,32,.7);"></div>',
    iconAnchor: [8, 8], className: ''
  });
  civAmbMarker = L.marker([ambLat, ambLon], { icon: ambIcon })
    .bindPopup('Ambulance location').addTo(civAlertMap);

  // My position marker (blue)
  if (civMyLat && civMyLon) {
    if (civMeMarker) civMeMarker.remove();
    const meIcon = L.divIcon({
      html: '<div style="background:#1354C4;width:12px;height:12px;border-radius:50%;border:2px solid #fff;"></div>',
      iconAnchor: [6, 6], className: ''
    });
    civMeMarker = L.marker([civMyLat, civMyLon], { icon: meIcon })
      .bindPopup('Your location').addTo(civAlertMap);
  }
}

/* ── Location sharing ───────────────────────────────────────── */
function startCivilianLocation() {
  if (!navigator.geolocation) {
    document.getElementById('civLocationText').textContent = 'GPS not available';
    return;
  }

  const opts = { enableHighAccuracy: true, timeout: 15000, maximumAge: 5000 };

  const watchId = navigator.geolocation.watchPosition(
    pos => {
      civMyLat = pos.coords.latitude;
      civMyLon = pos.coords.longitude;
      document.getElementById('civLocationDot').style.background = '#16a34a';
      document.getElementById('civLocationText').textContent =
        'Sharing — ' + civMyLat.toFixed(4) + ', ' + civMyLon.toFixed(4);

      sendCivLocation(civMyLat, civMyLon, pos.coords.heading, pos.coords.speed);

      // Update map if alert active
      if (civActiveAlert && civAlertMap && civMeMarker) {
        civMeMarker.setLatLng([civMyLat, civMyLon]);
      }
    },
    err => {
      document.getElementById('civLocationDot').style.background = '#f59e0b';
      document.getElementById('civLocationText').textContent = 'Location unavailable';
    },
    opts
  );

  // Send location every 5 seconds even without movement
  civLocInterval = setInterval(() => {
    if (civMyLat && civMyLon) {
      sendCivLocation(civMyLat, civMyLon, null, null);
    }
  }, 5000);

  // Store watchId for cleanup
  window._civWatchId = watchId;
}

async function sendCivLocation(lat, lon, heading, speed) {
  if (!civToken) return;
  try {
    await fetch('/api/civilian/location', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + civToken
      },
      body: JSON.stringify({ latitude: lat, longitude: lon, heading, speed })
    });
  } catch (e) { /* silent — will retry */ }
}

function stopCivilianLocation() {
  if (window._civWatchId != null) {
    navigator.geolocation.clearWatch(window._civWatchId);
    window._civWatchId = null;
  }
  if (civLocInterval) { clearInterval(civLocInterval); civLocInterval = null; }

  // Mark inactive on server
  if (civToken) {
    fetch('/api/civilian/location/stop', {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + civToken }
    }).catch(() => {});
  }
}

/* ── Logout ─────────────────────────────────────────────────── */
function civilianLogout() {
  stopCivilianLocation();
  if (civStompClient) { try { civStompClient.disconnect(); } catch(e){} }
  localStorage.removeItem('clearpath_token');
  localStorage.removeItem('clearpath_role');
  localStorage.removeItem('clearpath_civ_profile');
  localStorage.removeItem('clearpath_civ_phone');
  civToken = civProfile = civStompClient = civActiveAlert = null;
  dismissCivAlert();
  showRoleChooser();
}

/* ──────────────────────────────────────────────────────────────
   HELPERS
─────────────────────────────────────────────────────────────── */
function oppositeDirection(dir) {
  const opp = { N:'South', S:'North', E:'West', W:'East',
                NE:'Southwest', SW:'Northeast', NW:'Southeast', SE:'Northwest' };
  return opp[dir] || dir || '—';
}

function showCivError(msg) {
  const el = document.getElementById('civLoginErrorMsg');
  if (!el) return;
  el.textContent = msg;
  el.hidden = false;
}
function hideCivError() {
  const el = document.getElementById('civLoginErrorMsg');
  if (el) el.hidden = true;
}

function setBtn(id, label, disabled) {
  const btn = document.getElementById(id);
  if (!btn) return;
  btn.textContent = label;
  btn.disabled = disabled;
}
