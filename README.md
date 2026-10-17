# ClearPath: Emergency Traffic Clearance System

A law enforcement situational awareness application built for Traffic Police Officers using Java 17, Spring Boot 3, PostGIS, WebSockets (STOMP), and Leaflet PWA.

When an on-duty traffic police officer spots an approaching emergency ambulance, they trigger one dispatch action. ClearPath calculates spatial vectors, reverse-geocodes the corridor, and alerts every on-duty officer within 3 km AHEAD (+/- 60 degrees forward bearing cone or within 200 meters). Receiving officers receive an instant looping siren sound, phone vibration, and intersecting route details to clear crossroads before the emergency vehicle arrives. The transmission never travels backward.

---

## 1. System Architecture and Stack

- Backend: Java 17, Spring Boot 3.3.4, Spring Web, Spring Security (stateless JWT), Spring Data JPA, Spring WebSocket (STOMP over SockJS), Hibernate Spatial.
- Spatial Database: PostgreSQL with PostGIS extension (geography Point, SRID 4326 with GIST spatial index). Includes standalone in-memory spatial engine for local development.
- Telemetry and Push Dispatching:
  - Private STOMP queues (/user/queue/alerts) for zero-latency delivery to targeted officers.
  - Broadcast status channels (/topic/alerts/{alertId}) for live acknowledgment tracking.
  - Pluggable Firebase Cloud Messaging (FCM) adapter for background siren wakeups.
- Frontend: Responsive Progressive Web App (PWA) served directly by Spring Boot:
  - Government-grade Police Blue (#0B3D91), White, and Light Blue (#E8F0FB) design.
  - Emergency Red (#D32F2F) reserved exclusively for emergency triggers and alarms.
  - Clean rectangular enterprise buttons without pill shapes or purple gradients.
  - Leaflet.js with standard OpenStreetMap tiles, live GPS marker, and route display.
  - Web Audio API dual-tone Hi-Lo siren synthesizer (960 Hz / 770 Hz) and hardware vibration.
- Road Intelligence: OpenStreetMap Nominatim API for reverse geocoding road names and Overpass API for intersecting connecting roads.
- Compliance and Legal: Built-in Privacy Policy (/privacy.html) and Terms and Conditions (/terms.html) pages.

---

## 2. Targeting Logic (The Forward Cone)

To prevent notifying officers behind the emergency vehicle:
1. Radius Limitation: Candidate must be within 3.0 km Haversine distance.
2. Proximity Safety Override: Any officer within 200 meters (0.2 km) of the vehicle is always alerted for immediate intersection clearance.
3. Forward Cone Verification: For officers further than 200 meters away:
   - Initial forward bearing from the ambulance coordinates to the officer coordinates is calculated.
   - The minimal angular difference between the ambulance heading and the officer bearing must satisfy:
     Angular Difference <= 60.0 degrees
   - Any officer where Angular Difference > 60.0 degrees is strictly excluded.

---

## 3. Seeded Official Police Registry

ClearPath authenticates through the PoliceVerificationService interface. Pre-seeded sample officers:

| Officer ID | Name | Rank | Badge | Assigned Sector | Default Password |
|---|---|---|---|---|---|
| POLICE-101 | Inspector Rajesh Kumar | Inspector | TR-901 | Central Traffic Division | password123 |
| POLICE-102 | Sub-Inspector Vikram Singh | Sub-Inspector | TR-902 | MG Road Sector | password123 |
| POLICE-103 | Head Constable Anita Sharma | Head Constable | TR-903 | Outer Ring Road Outpost | password123 |
| POLICE-104 | Constable Suresh Patel | Constable | TR-904 | Highway Corridor Unit 4 | password123 |

---

## 4. REST and WebSocket Endpoints

### Authentication and Profile
- POST /api/auth/login : Verify credentials against official police registry and issue JWT.
- GET /api/me : Return verified profile details and current on-duty status.

### Duty Shift Management
- POST /api/duty/start : Mark officer as ON DUTY and begin live GPS telemetry.
- POST /api/duty/end : Mark officer as OFF DUTY and terminate telemetry.
- GET /api/duty/status : Return current shift status.

### Telemetry
- POST /api/location : Record officer GPS latitude, longitude, heading, and speed every 6 seconds.

### Emergency Dispatch
- POST /api/alerts : Spot ambulance, select heading (8 compass directions or GPS heading), trigger forward alert.
- POST /api/alerts/{id}/ack : Acknowledge alert ("I am clearing the road").
- POST /api/alerts/{id}/dismiss : Dismiss alert on receiver terminal.
- POST /api/alerts/{id}/complete : Mark ambulance passed to clear the alert.
- GET /api/alerts/active : List active alerts affecting the authenticated officer.
- GET /api/alerts/history : Alert history for audit review.

### WebSocket Topics
- Private queue: /user/queue/alerts : Direct emergency siren transmission.
- Broadcast topic: /topic/alerts/{alertId} : Real-time acknowledgment count updates.

---

## 5. Local Setup and Execution

### Running the Executable JAR
```bash
cd C:\Users\aleem\Desktop\traffic_police
java -jar target/clearpath-backend-1.0.0.jar
```
Navigate to http://localhost:8080 in any browser.

### Running with Maven
```bash
mvn spring-boot:run
```

### Running with Docker Compose (PostgreSQL with PostGIS)
```bash
docker compose up -d
```

---

## 6. Custom Domain and Production Deployment Guide

Before deploying ClearPath into live law enforcement production:

1. DNS Configuration:
   - Point an A record (e.g. dispatch.police.gov) to your server static IP.
   - Or configure a CNAME record pointing to your cloud container service (Render, AWS ALB, Railway).

2. Reverse Proxy and TLS Termination (Nginx Example):
   ```nginx
   server {
       listen 80;
       server_name dispatch.police.gov;
       return 301 https://$host$request_uri;
   }

   server {
       listen 443 ssl http2;
       server_name dispatch.police.gov;

       ssl_certificate /etc/letsencrypt/live/dispatch.police.gov/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/dispatch.police.gov/privkey.pem;

       location / {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto https;

           # WebSocket support
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }
   }
   ```

3. Verification Checklist:
   - Custom domain mapped and resolving.
   - HTTPS enforced with TLS 1.2 or 1.3 certificate.
   - Favicon loaded at /favicon.svg and /favicon.ico.
   - Privacy Policy accessible at /privacy.html.
   - Terms and Conditions accessible at /terms.html.
   - Zero synthetic marketing banners or promotional counters.

---

## 7. Two-Terminal Operational Testing Guide

You can test ClearPath using two browser sessions side-by-side (Regular Window and Incognito Window), or on two separate devices.

### Terminal 1: Officer Spotting Emergency Vehicle (Sender)
1. Navigate to http://localhost:8080.
2. Select POLICE-101 (Inspector Rajesh Kumar) and click "VERIFY CREDENTIALS & LOGIN".
3. Under Simulated Deployment Coordinates, select "Ambulance Origin (MG Rd)".
4. Click "START ACTIVE DUTY SHIFT".

### Terminal 2: Officer Ahead in Route (Receiver)
1. Open an Incognito Window (Ctrl + Shift + N) and navigate to http://localhost:8080.
2. Select POLICE-102 (SI Vikram Singh) and click "VERIFY CREDENTIALS & LOGIN".
3. Under Simulated Deployment Coordinates, select "Officer Ahead (1 km North)".
4. Click "START ACTIVE DUTY SHIFT".

### Negative Control: Officer Behind (Optional)
- Open a third session with POLICE-103 (HC Anita Sharma), select "Officer Behind (1 km South)", and click "START ACTIVE DUTY SHIFT".

### Dispatch Execution:
1. On Terminal 1 (Sender), click "AMBULANCE SPOTTED (EMERGENCY DISPATCH)".
2. Select direction "N (0 deg)" (facing North toward Officer 2).
3. Select vehicle category and click "CONFIRM AND DISPATCH".

### Operational Response:
- Terminal 2 (Ahead): Looping ambulance siren sounds immediately. High-contrast alert banner displays sender name, road name, distance (1.00 km), and connecting junctions to clear. Click "I AM CLEARING THE ROAD" to acknowledge and silence the alarm.
- Terminal 1 (Sender): Active status widget increments live to "1 / 1 Acknowledged". Click "Ambulance Passed" to complete the corridor clearance.
- Terminal 3 (Behind): Receives zero notifications because the vector is behind the vehicle.
