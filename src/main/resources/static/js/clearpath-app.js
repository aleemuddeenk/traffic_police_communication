/**
 * ClearPath - Emergency Traffic Clearance Client Application
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
  behind: { lat: 12.9626, lon: 77.5946, label: '1 km South (Behind - Filtered Out)' }
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
      .then(reg => console.log('ClearPath Service Worker registered'))
      .catch(err => console.log('SW registration error:', err));
  }
}

function initUIEvents() {
  document.getElementById('loginBtn').addEventListener('click', handleLogin);
  document.getElementById('startDutyBtn').addEventListener('click', handleStartDuty);
  document.getElementById('endDutyBtn').addEventListener('click', handleEndDuty);
  document.getElementById('logoutBtn').addEventListener('click', handleLogout);
  document.getElementById('openTriggerModalBtn').addEventListener('click', openTriggerModal);
  document.getElementById('confirmTriggerBtn').addEventListener('click', handleTriggerAlert);
  document.getElementById('ackAlertBtn').addEventListener('click', handleAcknowledgeAlert);
  document.getElementById('dismissAlertBtn').addEventListener('click', handleDismissAlert);
  document.getElementById('ambulancePassedBtn').addEventListener('click', handleAmbulancePassed);
}

function showScreen(screenId) {
  document.getElementById('loginScreen').style.display = screenId === 'loginScreen' ? 'block' : 'none';
  document.getElementById('homeScreen').style.display = screenId === 'homeScreen' ? 'block' : 'none';
  document.getElementById('dutyScreen').style.display = screenId === 'dutyScreen' ? 'flex' : 'none';

  if (screenId === 'dutyScreen' && AppState.map) {
    setTimeout(() => AppState.map.invalidateSize(), 200);
  }
}

function fillCredentials(id, label) {
  document.getElementById('officerIdInput').value = id;
  document.getElementById('passwordInput').value = 'password123';
  const errBox = document.getElementById('loginErrorMsg');
  errBox.style.display = 'block';
  errBox.style.background = '#E0F2FE';
  errBox.style.color = '#0369A1';
  errBox.innerText = `Selected: ${label}`;
}

async function handleLogin() {
  const officerId = document.getElementById('officerIdInput').value.trim();
  const password = document.getElementById('passwordInput').value;
  const errBox = document.getElementById('loginErrorMsg');

  if (!officerId || !password) {
    errBox.innerText = 'Officer ID and password are required.';
    errBox.style.display = 'block';
    return;
  }

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ officerId, password })
    });

    const data = await res.json();
    if (!res.ok) {
      errBox.innerText = data.message || 'Verification failed against police records.';
      errBox.style.display = 'block';
      return;
    }

    AppState.token = data.token;
    localStorage.setItem('clearpath_jwt', data.token);
    errBox.style.display = 'none';

    setOfficerProfile(data.profile);
  } catch (err) {
    errBox.innerText = 'Unable to connect to verification server.';
    errBox.style.display = 'block';
  }
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

  document.getElementById('profileName').innerText = profile.name;
  document.getElementById('profileRank').innerText = profile.rank.toUpperCase();
  document.getElementById('profileBadge').innerText = profile.badgeNumber;
  document.getElementById('profileOfficerId').innerText = profile.officerId;
  document.getElementById('profileStation').innerText = `${profile.station} • ${profile.district}`;

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
  const badge = document.getElementById('headerStatusBadge');
  const text = document.getElementById('headerStatusText');

  if (AppState.isOnDuty) {
    badge.className = 'header-status-badge on-duty';
    text.innerText = 'ON DUTY';
  } else if (AppState.officer) {
    badge.className = 'header-status-badge';
    text.innerText = 'OFF DUTY';
  } else {
    badge.className = 'header-status-badge';
    text.innerText = 'OFFLINE';
  }
}

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
        AppState.coords.lat = pos.coords.latitude;
        AppState.coords.lon = pos.coords.longitude;
        AppState.coords.heading = pos.coords.heading || 0;
        AppState.coords.speed = pos.coords.speed || 0;
      } catch (locErr) {
        console.warn('GPS location request timed out or denied, using central outpost coordinates');
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
    document.getElementById('permissionDeniedNotice').style.display = 'block';
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
  if (AppState.pingInterval) {
    clearInterval(AppState.pingInterval);
    AppState.pingInterval = null;
  }
  if (AppState.stompClient) {
    AppState.stompClient.disconnect();
  }
  updateHeaderStatus();
  showScreen('homeScreen');
}

function handleLogout() {
  localStorage.removeItem('clearpath_jwt');
  AppState.token = null;
  AppState.officer = null;
  AppState.isOnDuty = false;
  if (AppState.pingInterval) clearInterval(AppState.pingInterval);
  if (AppState.stompClient) AppState.stompClient.disconnect();
  updateHeaderStatus();
  showScreen('loginScreen');
}

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

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19
  }).addTo(AppState.map);

  const officerIcon = L.divIcon({
    className: 'officer-map-pin',
    html: `<div style="background:#0B3D91; border:2.5px solid #FFFFFF; width:20px; height:20px; border-radius:3px; box-shadow:0 1px 4px rgba(11,61,145,0.4);"></div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10]
  });

  AppState.officerMarker = L.marker([AppState.coords.lat, AppState.coords.lon], { icon: officerIcon })
    .addTo(AppState.map)
    .bindPopup(`<b>${AppState.officer.rank} ${AppState.officer.name}</b><br>ID: ${AppState.officer.officerId}`);

  updateCoordsDisplay();
}

function updateCoordsDisplay() {
  document.getElementById('currentCoords').innerText =
    `Lat: ${AppState.coords.lat.toFixed(4)}, Lon: ${AppState.coords.lon.toFixed(4)}`;
}

function startLocationTracking() {
  transmitLocation();

  if (!AppState.pingInterval) {
    AppState.pingInterval = setInterval(transmitLocation, 6000); // Every 6s
  }

  if (!AppState.simulated && 'geolocation' in navigator) {
    navigator.geolocation.watchPosition(pos => {
      if (!AppState.simulated) {
        AppState.coords.lat = pos.coords.latitude;
        AppState.coords.lon = pos.coords.longitude;
        AppState.coords.heading = pos.coords.heading || 0;
        AppState.coords.speed = pos.coords.speed || 0;
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
        latitude: AppState.coords.lat,
        longitude: AppState.coords.lon,
        heading: AppState.coords.heading,
        speed: AppState.coords.speed
      })
    });
    if (res.ok) {
      document.getElementById('currentRoadName').innerText = 'Patrol Post (GPS Active)';
    }
  } catch (e) {
    console.debug('Telemetry ping skipped:', e);
  }
}

function connectWebSocket() {
  if (AppState.stompClient && AppState.stompClient.connected) return;

  const socket = new SockJS('/ws');
  AppState.stompClient = Stomp.over(socket);
  AppState.stompClient.debug = null; // Quiet console

  AppState.stompClient.connect({
    'Authorization': `Bearer ${AppState.token}`,
    'token': AppState.token
  }, frame => {
    console.log('Connected to ClearPath STOMP Dispatch');

    // Subscribe to private emergency alerts directed at this officer
    AppState.stompClient.subscribe('/user/queue/alerts', message => {
      const alertData = JSON.parse(message.body);
      handleIncomingEmergencyAlert(alertData);
    });

    if (AppState.activeSenderAlertId) {
      subscribeToAlertUpdates(AppState.activeSenderAlertId);
    }
  }, error => {
    console.warn('STOMP connection reconnecting in 5s...', error);
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
      alert('Ambulance alert has cleared. Traffic flow normal.');
    } else {
      document.getElementById('senderAckCounter').innerText =
        `${data.acknowledgedCount} / ${data.notifiedCount} Acknowledged`;
    }
  });
}

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
}

async function handleTriggerAlert() {
  const confirmBtn = document.getElementById('confirmTriggerBtn');
  confirmBtn.disabled = true;
  confirmBtn.innerText = 'DISPATCHING...';

  const type = document.getElementById('ambulanceTypeSelect').value;
  const plate = document.getElementById('ambulancePlateInput').value.trim();

  try {
    const res = await fetch('/api/alerts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${AppState.token}`
      },
      body: JSON.stringify({
        latitude: AppState.coords.lat,
        longitude: AppState.coords.lon,
        compassDirection: AppState.selectedCompass,
        ambulanceType: type,
        ambulancePlate: plate
      })
    });

    const data = await res.json();
    if (!res.ok) {
      alert(data.message || 'Trigger rejected.');
      return;
    }

    closeTriggerModal();
    AppState.activeSenderAlertId = data.alertId;

    // Show Sender Status bar
    document.getElementById('senderRoadLabel').innerText = `${data.roadName || 'Main Corridor'} [Facing ${data.compassDirection}]`;
    document.getElementById('senderAckCounter').innerText = `0 / ${data.notifiedCount} Acknowledged`;
    document.getElementById('senderConnectingRoads').innerText =
      data.connectingRoads && data.connectingRoads.length > 0 ? data.connectingRoads.join(', ') : 'Arterial Junctions';
    document.getElementById('senderActiveStatus').style.display = 'block';

    subscribeToAlertUpdates(data.alertId);

    // Plot ambulance marker on map
    if (AppState.map) {
      if (AppState.ambulanceMarker) AppState.map.removeLayer(AppState.ambulanceMarker);
      const ambIcon = L.divIcon({
        className: 'amb-map-pin',
        html: `<div style="background:#D32F2F; border:2px solid #FFFFFF; width:26px; height:26px; border-radius:3px; display:flex; align-items:center; justify-content:center; color:white; font-weight:800; font-size:13px; box-shadow:0 2px 6px rgba(211,47,47,0.5);">A</div>`,
        iconSize: [26, 26],
        iconAnchor: [13, 13]
      });
      AppState.ambulanceMarker = L.marker([AppState.coords.lat, AppState.coords.lon], { icon: ambIcon })
        .addTo(AppState.map)
        .bindPopup(`<b>Ambulance Dispatched Ahead (${data.compassDirection})</b><br>${data.notifiedCount} officers alerted`)
        .openPopup();
    }

    alert(`Emergency alert dispatched: ${data.notifiedCount} officers ahead within 3 km have been alerted.`);
  } catch (err) {
    alert('Failed to trigger alert. Check connection.');
  } finally {
    confirmBtn.disabled = false;
    confirmBtn.innerText = 'CONFIRM & DISPATCH';
  }
}

function handleIncomingEmergencyAlert(alert) {
  AppState.currentIncomingAlert = alert;

  // Start looping emergency siren audio and phone vibration
  window.ambulanceSiren.start();

  document.getElementById('alertSenderName').innerText = `${alert.sender.rank} ${alert.sender.name}`;
  document.getElementById('alertSenderBadge').innerText = `${alert.sender.badgeNumber} (${alert.sender.station})`;
  document.getElementById('alertRoadName').innerText = alert.roadName || 'Main Road Corridor';
  document.getElementById('alertDistanceVal').innerText = `${alert.distanceKm.toFixed(2)} km away`;
  document.getElementById('alertHeadingVal').innerText = `Facing ${alert.compassDirection} (${alert.headingDeg.toFixed(0)}°)`;

  const roadList = document.getElementById('alertConnectingRoadsList');
  roadList.innerHTML = '';
  if (alert.connectingRoads && alert.connectingRoads.length > 0) {
    alert.connectingRoads.forEach(r => {
      const span = document.createElement('span');
      span.className = 'connecting-road-badge';
      span.innerText = r;
      roadList.appendChild(span);
    });
  } else {
    roadList.innerHTML = '<span class="connecting-road-badge">Forward Junctions</span>';
  }

  // Display full screen high priority siren alert
  document.getElementById('sirenAlertOverlay').className = 'siren-alert-container active';
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
    alert('Acknowledgment recorded: You are clearing the route.');
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
          document.getElementById('senderRoadLabel').innerText = `${first.roadName || 'Corridor'} [Facing ${first.compassDirection}]`;
          document.getElementById('senderAckCounter').innerText = `${first.acknowledgedCount} / ${first.notifiedCount} Acknowledged`;
          document.getElementById('senderActiveStatus').style.display = 'block';
          subscribeToAlertUpdates(first.alertId);
        }
      }
    }
  } catch (e) {}
}
