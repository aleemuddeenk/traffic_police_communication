# ClearPath - Emergency Traffic Clearance System

> **A government-grade full-stack real-time situational awareness application built for Traffic Police Officers using Java 17+, Spring Boot 3, PostGIS, WebSockets (STOMP), and Leaflet PWA.**

When any traffic police officer spots an approaching ambulance, they press one button. ClearPath instantly calculates spatial vectors, reverse-geocodes the corridor, and alerts every on-duty officer within **3 km AHEAD** ($\pm 60^\circ$ forward bearing cone or within 200 m). Receiving officers get an instant looping siren sound, phone vibration, and route details to clear intersecting roads before the ambulance arrives. **The message never travels backwards.**

---

## Tech Stack

- **Backend**: Java 17/21/25, Spring Boot 3.3.4, Spring Web, Spring Security (stateless JWT), Spring Data JPA, Spring WebSocket (STOMP over SockJS), Hibernate Spatial.
- **Geographic Database**: PostgreSQL with PostGIS extension (`geography(Point, 4326)` with GIST index). Includes in-memory spatial engine fallback for zero-dependency local testing.
- **Alert Dispatching**:
  - WebSocket STOMP private queues (`/user/queue/alerts`) for zero-latency in-app delivery.
  - Broadcast status topics (`/topic/alerts/{alertId}`) for live acknowledgment tracking.
  - Firebase Cloud Messaging (FCM) adapter for background wakeups with high-priority siren payload.
- **Frontend PWA**:
  - Responsive, government-grade Blue & White interface (`#0B3D91` police blue, `#FFFFFF`, `#E8F0FB`, `#D32F2F` emergency red).
  - OpenStreetMap & Leaflet.js with live GPS tracking, compass heading cone, and pulse animations.
  - Real-time Web Audio API siren synthesizer: generates looping European Hi-Lo (960 Hz / 770 Hz) emergency siren sound directly in the browser without external media file dependencies.
  - Hardware Vibration API.
- **Road Network**: OpenStreetMap Nominatim API for road names + Overpass API for forward connecting roads and junctions.

---

## Targeting Logic (The Forward Cone)

To prevent notifying officers behind the ambulance:
1. **Radius Check**: Candidate must be within $3.0\text{ km}$ Haversine distance.
2. **Proximity Safety Override**: Any officer within $\le 200\text{ m}$ ($0.2\text{ km}$) of the ambulance is **always alerted** for immediate intersection clearance.
3. **Forward Cone Verification**: For officers $> 200\text{ m}$ away:
   - Initial forward azimuth from the ambulance $(lat_1, lon_1)$ to the officer $(lat_2, lon_2)$ is computed.
   - The minimal angular difference between the ambulance heading $(\theta_{\text{amb}})$ and the officer bearing $(\theta_{\text{officer}})$ must satisfy:
     $$\Delta\theta \le 60.0^\circ$$
   - Any officer where $\Delta\theta > 60.0^\circ$ is **strictly excluded** (never notified).

---

## Seeded Police Verification Database

ClearPath connects to an official police directory via `PoliceVerificationService`. Demo officers are pre-seeded:

| Officer ID | Name | Rank | Badge | Station / Outpost | Password |
|---|---|---|---|---|---|
| `POLICE-101` | Inspector Rajesh Kumar | Inspector | TR-901 | Central Traffic Division | `password123` |
| `POLICE-102` | Sub-Inspector Vikram Singh | Sub-Inspector | TR-902 | MG Road Junction Sector | `password123` |
| `POLICE-103` | Head Constable Anita Sharma | Head Constable | TR-903 | Outer Ring Road Outpost | `password123` |
| `POLICE-104` | Constable Suresh Patel | Constable | TR-904 | Highway Corridor Unit 4 | `password123` |

---

## REST & WebSocket API

### Authentication & Profile
- `POST /api/auth/login` - Verify officer badge credentials against police database and issue JWT.
- `GET /api/me` - Fetch verified profile details and current on-duty status.

### Duty Shift Management
- `POST /api/duty/start` - Officer goes ON DUTY and activates live GPS tracking.
- `POST /api/duty/end` - Officer goes OFF DUTY and stops location telemetry.
- `GET /api/duty/status` - Check current duty state.

### Telemetry
- `POST /api/location` - Transmit GPS latitude, longitude, heading, and speed every 5-10s.

### Emergency Alerts
- `POST /api/alerts` - Spot ambulance, select heading (8 compass directions or GPS heading), trigger forward alert.
- `POST /api/alerts/{id}/ack` - Officer acknowledges ("I'm clearing the road").
- `POST /api/alerts/{id}/dismiss` - Dismisses alert on receiver device.
- `POST /api/alerts/{id}/complete` - Sender marks "Ambulance passed" to clear the alert.
- `GET /api/alerts/active` - List active alerts affecting current officer.
- `GET /api/alerts/history` - Officer alert dispatch history.

### WebSocket Destinations
- Private: `/user/queue/alerts` - Delivers real-time emergency siren popups.
- Broadcast: `/topic/alerts/{alertId}` - Live updates on officers notified and acknowledged.

---

## Quick Start & Running

### Option 1: Run with Maven (Instant Zero-Setup Mode)
The application includes an in-memory spatial database for instant local development:

```bash
cd traffic_police
mvn spring-boot:run
```
Open **`http://localhost:8080`** in any browser.

### Option 2: Run with Docker Compose (PostgreSQL + PostGIS)
```bash
docker compose up --build
```
This boots:
1. `clearpath-postgis` on port `5432` with PostGIS 15.
2. `clearpath-app` on port `8080`.

---

## Step-by-Step 2-Device Testing Guide

You can test ClearPath using two smartphones on the same Wi-Fi, or two browser windows (e.g., Chrome and Chrome Incognito).

### Device 1: Sender (Officer Spotting Ambulance)
1. Open `http://localhost:8080` (or your local IP `http://<YOUR_IP>:8080` on phone).
2. Tap **POLICE-101 (Inspector Rajesh Kumar)** to auto-fill credentials, then click **VERIFY & LOGIN**.
3. Under *Testing Mode Coordinates*, tap **Ambulance Origin (MG Rd)**.
4. Click **START DUTY**. The Leaflet map opens with a blue patrol pin.

### Device 2: Receiver (Officer Ahead in Path)
1. Open `http://localhost:8080` in an **Incognito / Private window** (or on a second phone).
2. Tap **POLICE-102 (SI Vikram Singh)** and click **VERIFY & LOGIN**.
3. Under *Testing Mode Coordinates*, tap **Officer Ahead (1 km North)**.
4. Click **START DUTY**.

*(Optional) Device 3: Officer Behind (Negative Control)*
- Log in as **POLICE-103 (HC Anita Sharma)**, set location to **Officer Behind (1 km South)**, and click **START DUTY**.

### Trigger the Emergency Alert
1. On **Device 1**, tap the big red **AMBULANCE SPOTTED** button.
2. The compass dial appears. Select **N (0°)** (heading North toward Officer 2).
3. Select ambulance type (e.g., *Advanced Life Support*), and click **CONFIRM & DISPATCH**.

### Observe the Results
- **Device 2 (Officer Ahead)**:
  - Loud looping ambulance siren sound plays immediately!
  - Screen flashes with full-screen emergency red strobe.
  - Displays: *Spotted by Insp. Rajesh Kumar*, *Road: MG Road*, *Distance: ~1.0 km away*, *Heading: North*, and connecting junctions to clear.
  - Tap **"I'M CLEARING THE ROAD"**. The siren stops.
- **Device 1 (Sender)**:
  - Sender status widget updates in real-time to **"1 / 1 Acknowledged"**!
  - Tap **"Ambulance Passed"** to mark the corridor clear.
- **Device 3 (Officer Behind)**:
  - Received **ZERO alerts** (the alert never traveled backward).
