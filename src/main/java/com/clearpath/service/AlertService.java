package com.clearpath.service;

import com.clearpath.dto.AlertAckResponseDto;
import com.clearpath.dto.AlertResponseDto;
import com.clearpath.dto.CreateAlertRequest;
import com.clearpath.geo.RoadNetworkService;
import com.clearpath.geo.SpatialMathService;
import com.clearpath.model.AlertRecipient;
import com.clearpath.model.AmbulanceAlert;
import com.clearpath.model.LocationPing;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.AlertStatus;
import com.clearpath.model.enums.RecipientStatus;
import com.clearpath.repository.AlertRecipientRepository;
import com.clearpath.repository.AmbulanceAlertRepository;
import com.clearpath.repository.LocationPingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AmbulanceAlertRepository alertRepository;
    private final AlertRecipientRepository recipientRepository;
    private final LocationPingRepository locationPingRepository;
    private final SpatialMathService spatialMathService;
    private final RoadNetworkService roadNetworkService;
    private final DutyService dutyService;
    private final RateLimitingService rateLimitingService;
    private final AuditService auditService;
    private final FcmNotificationService fcmNotificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public AlertService(
            AmbulanceAlertRepository alertRepository,
            AlertRecipientRepository recipientRepository,
            LocationPingRepository locationPingRepository,
            SpatialMathService spatialMathService,
            RoadNetworkService roadNetworkService,
            DutyService dutyService,
            RateLimitingService rateLimitingService,
            AuditService auditService,
            FcmNotificationService fcmNotificationService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.alertRepository = alertRepository;
        this.recipientRepository = recipientRepository;
        this.locationPingRepository = locationPingRepository;
        this.spatialMathService = spatialMathService;
        this.roadNetworkService = roadNetworkService;
        this.dutyService = dutyService;
        this.rateLimitingService = rateLimitingService;
        this.auditService = auditService;
        this.fcmNotificationService = fcmNotificationService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AlertResponseDto triggerAlert(Officer sender, CreateAlertRequest request) {
        if (!dutyService.isOnDuty(sender)) {
            throw new IllegalStateException("Only ON-DUTY officers can spot and trigger ambulance alerts.");
        }

        if (!rateLimitingService.checkAndAcquireAlertTrigger(sender.getOfficerId())) {
            throw new IllegalStateException("Please wait a few seconds before triggering another alert.");
        }

        // Determine heading degrees
        double headingDeg = 0.0;
        if (request.headingDeg() != null) {
            headingDeg = (request.headingDeg() % 360.0 + 360.0) % 360.0;
        } else if (request.compassDirection() != null && !request.compassDirection().isBlank()) {
            headingDeg = spatialMathService.compassToDegrees(request.compassDirection());
        }

        String compassDirection = spatialMathService.degreesToCompass(headingDeg);

        // Reverse-geocode road name and connecting junctions ahead
        String roadName = roadNetworkService.resolveRoadName(request.latitude(), request.longitude());
        List<String> connectingRoads = roadNetworkService.resolveConnectingRoads(
                request.latitude(), request.longitude(), headingDeg, roadName);

        String connectingRoadsJson = null;
        try {
            connectingRoadsJson = objectMapper.writeValueAsString(connectingRoads);
        } catch (Exception e) {
            log.error("Failed to serialize connecting roads", e);
        }

        // Create alert entity (active for 8 minutes)
        Instant expiresAt = Instant.now().plusSeconds(480);
        AmbulanceAlert alert = new AmbulanceAlert(
                sender,
                request.latitude(),
                request.longitude(),
                headingDeg,
                roadName,
                request.ambulanceType() != null && !request.ambulanceType().isBlank() ? request.ambulanceType() : "Emergency Ambulance",
                request.ambulancePlate(),
                expiresAt
        );
        alert.setConnectingRoadsJson(connectingRoadsJson);
        AmbulanceAlert savedAlert = alertRepository.save(alert);

        // Spatial query candidates within 3.5 km bounding box (to cover 3.0 km sphere)
        SpatialMathService.BoundingBox bbox = spatialMathService.calculateBoundingBox(
                request.latitude(), request.longitude(), 3.5);

        Instant recentThreshold = Instant.now().minusSeconds(1800); // 30 minutes
        List<LocationPing> candidates = locationPingRepository.findOnDutyCandidatesInBoundingBox(
                sender.getId(), bbox.minLat(), bbox.maxLat(), bbox.minLon(), bbox.maxLon(), recentThreshold);

        // Fallback: If bounding box query returned none, check all active officers in system (e.g. for demo/tests)
        if (candidates.isEmpty()) {
            candidates = locationPingRepository.findAllOnDutyOfficersExceptSender(sender.getId());
        }

        List<AlertRecipient> qualifiedRecipients = new ArrayList<>();

        for (LocationPing ping : candidates) {
            SpatialMathService.TargetCheckResult eval = spatialMathService.evaluateTarget(
                    request.latitude(),
                    request.longitude(),
                    headingDeg,
                    ping.getLatitude(),
                    ping.getLongitude()
            );

            // Strict forward-only filter: must be within 3 km AND (<= 200m OR within +/- 60 deg cone)
            if (eval.isEligible()) {
                AlertRecipient recipient = new AlertRecipient(
                        savedAlert,
                        ping.getOfficer(),
                        eval.distanceKm(),
                        eval.bearingDeg(),
                        true
                );
                qualifiedRecipients.add(recipient);
            } else {
                log.info("Officer {} excluded from alert #{}: {} (dist: {:.2f} km, bearing: {:.1f} deg, diff: {:.1f} deg)",
                        ping.getOfficer().getOfficerId(), savedAlert.getId(), eval.reason(),
                        eval.distanceKm(), eval.bearingDeg(), eval.angularDiffDeg());
            }
        }

        recipientRepository.saveAll(qualifiedRecipients);

        // Dispatch notifications to each qualified officer
        for (AlertRecipient recip : qualifiedRecipients) {
            AlertResponseDto recipientPayload = toRecipientResponseDto(savedAlert, recip, connectingRoads);
            // WebSocket private user destination: /user/{officerId}/queue/alerts
            messagingTemplate.convertAndSendToUser(
                    recip.getOfficer().getOfficerId(),
                    "/queue/alerts",
                    recipientPayload
            );
            // FCM push notification (background siren)
            fcmNotificationService.sendEmergencyPushNotification(recip, savedAlert);
        }

        // Audit log the alert trigger
        List<String> notifiedOfficerIds = qualifiedRecipients.stream()
                .map(r -> r.getOfficer().getOfficerId())
                .collect(Collectors.toList());

        Map<String, Object> auditDetails = Map.of(
                "roadName", roadName,
                "headingDeg", headingDeg,
                "compass", compassDirection,
                "notifiedCount", qualifiedRecipients.size(),
                "notifiedOfficers", notifiedOfficerIds,
                "connectingRoads", connectingRoads
        );
        auditService.logAction(savedAlert.getId(), "ALERT_TRIGGERED", sender.getOfficerId(), auditDetails);

        return toSenderResponseDto(savedAlert, connectingRoads, qualifiedRecipients.size(), 0);
    }

    @Transactional
    public AlertAckResponseDto acknowledgeAlert(Officer officer, Long alertId) {
        AmbulanceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        AlertRecipient recipient = recipientRepository.findByAlertAndOfficer(alert, officer)
                .orElseThrow(() -> new IllegalStateException("Officer was not targeted for this alert"));

        if (recipient.getStatus() != RecipientStatus.ACKNOWLEDGED) {
            recipient.setStatus(RecipientStatus.ACKNOWLEDGED);
            recipient.setAcknowledgedAt(Instant.now());
            recipientRepository.save(recipient);
        }

        long notifiedCount = recipientRepository.countByAlert(alert);
        long ackCount = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);

        // Broadcast real-time acknowledgment update to sender and listeners
        AlertAckResponseDto updateDto = new AlertAckResponseDto(
                alert.getId(),
                alert.getStatus().name(),
                ackCount,
                notifiedCount,
                officer.getName() + " is clearing the road!"
        );

        messagingTemplate.convertAndSend("/topic/alerts/" + alert.getId(), updateDto);
        auditService.logAction(alert.getId(), "ALERT_ACKNOWLEDGED", officer.getOfficerId(),
                Map.of("officerName", officer.getName(), "ackCount", ackCount, "total", notifiedCount));

        return updateDto;
    }

    @Transactional
    public AlertAckResponseDto dismissAlert(Officer officer, Long alertId) {
        AmbulanceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        AlertRecipient recipient = recipientRepository.findByAlertAndOfficer(alert, officer)
                .orElseThrow(() -> new IllegalStateException("Officer was not targeted for this alert"));

        recipient.setStatus(RecipientStatus.DISMISSED);
        recipientRepository.save(recipient);

        long notifiedCount = recipientRepository.countByAlert(alert);
        long ackCount = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);

        return new AlertAckResponseDto(alert.getId(), alert.getStatus().name(), ackCount, notifiedCount, "Alert dismissed");
    }

    @Transactional
    public AlertAckResponseDto markAmbulancePassed(Officer sender, Long alertId) {
        AmbulanceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        if (!alert.getSenderOfficer().getId().equals(sender.getId())) {
            throw new IllegalStateException("Only the officer who spotted the ambulance can mark it as passed.");
        }

        alert.setStatus(AlertStatus.PASSED);
        alertRepository.save(alert);

        long notifiedCount = recipientRepository.countByAlert(alert);
        long ackCount = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);

        AlertAckResponseDto updateDto = new AlertAckResponseDto(
                alert.getId(),
                AlertStatus.PASSED.name(),
                ackCount,
                notifiedCount,
                "Ambulance has passed. Route is clear."
        );

        // Broadcast to all participating officers
        messagingTemplate.convertAndSend("/topic/alerts/" + alert.getId(), updateDto);
        auditService.logAction(alert.getId(), "AMBULANCE_PASSED", sender.getOfficerId(), "Sender marked ambulance passed");

        return updateDto;
    }

    @Scheduled(fixedRate = 30000) // every 30 seconds
    @Transactional
    public void expireOldAlerts() {
        List<AmbulanceAlert> expired = alertRepository.findByStatusAndExpiresAtBefore(AlertStatus.ACTIVE, Instant.now());
        for (AmbulanceAlert alert : expired) {
            alert.setStatus(AlertStatus.EXPIRED);
            alertRepository.save(alert);
            long notifiedCount = recipientRepository.countByAlert(alert);
            long ackCount = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);
            AlertAckResponseDto updateDto = new AlertAckResponseDto(
                    alert.getId(),
                    AlertStatus.EXPIRED.name(),
                    ackCount,
                    notifiedCount,
                    "Alert has expired."
            );
            messagingTemplate.convertAndSend("/topic/alerts/" + alert.getId(), updateDto);
            log.info("Expired active alert #{}", alert.getId());
        }
    }

    public List<AlertResponseDto> getActiveAlertsForOfficer(Officer officer) {
        List<AmbulanceAlert> activeAlerts = alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.ACTIVE);
        List<AlertResponseDto> result = new ArrayList<>();

        for (AmbulanceAlert alert : activeAlerts) {
            // Check if sender
            if (alert.getSenderOfficer().getId().equals(officer.getId())) {
                long notifiedCount = recipientRepository.countByAlert(alert);
                long ackCount = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);
                result.add(toSenderResponseDto(alert, parseConnectingRoads(alert.getConnectingRoadsJson()), notifiedCount, ackCount));
            } else {
                // Check if recipient
                Optional<AlertRecipient> recipOpt = recipientRepository.findByAlertAndOfficer(alert, officer);
                recipOpt.ifPresent(r -> result.add(toRecipientResponseDto(alert, r, parseConnectingRoads(alert.getConnectingRoadsJson()))));
            }
        }
        return result;
    }

    public List<AlertResponseDto> getOfficerAlertHistory(Officer officer) {
        List<AlertRecipient> received = recipientRepository.findByOfficerOrderByNotifiedAtDesc(officer);
        return received.stream()
                .map(r -> toRecipientResponseDto(r.getAlert(), r, parseConnectingRoads(r.getAlert().getConnectingRoadsJson())))
                .collect(Collectors.toList());
    }

    public Optional<AlertResponseDto> getAlertStatus(Long alertId) {
        return alertRepository.findById(alertId).map(alert -> {
            long notified = recipientRepository.countByAlert(alert);
            long ack = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);
            return toSenderResponseDto(alert, parseConnectingRoads(alert.getConnectingRoadsJson()), notified, ack);
        });
    }

    private AlertResponseDto toSenderResponseDto(AmbulanceAlert alert, List<String> connectingRoads, long notified, long ack) {
        Officer sender = alert.getSenderOfficer();
        AlertResponseDto.SenderInfo senderInfo = new AlertResponseDto.SenderInfo(
                sender.getId(),
                sender.getOfficerId(),
                sender.getName(),
                sender.getRank(),
                sender.getBadgeNumber(),
                sender.getStation(),
                sender.getPhone(),
                sender.getPhotoUrl()
        );

        return new AlertResponseDto(
                alert.getId(),
                senderInfo,
                alert.getRoadName(),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getHeadingDeg(),
                spatialMathService.degreesToCompass(alert.getHeadingDeg()),
                alert.getAmbulanceType(),
                alert.getAmbulancePlate(),
                alert.getStatus().name(),
                0.0,
                alert.getHeadingDeg(),
                connectingRoads,
                alert.getCreatedAt(),
                alert.getExpiresAt(),
                notified,
                ack
        );
    }

    private AlertResponseDto toRecipientResponseDto(AmbulanceAlert alert, AlertRecipient recipient, List<String> connectingRoads) {
        Officer sender = alert.getSenderOfficer();
        AlertResponseDto.SenderInfo senderInfo = new AlertResponseDto.SenderInfo(
                sender.getId(),
                sender.getOfficerId(),
                sender.getName(),
                sender.getRank(),
                sender.getBadgeNumber(),
                sender.getStation(),
                sender.getPhone(),
                sender.getPhotoUrl()
        );

        long notified = recipientRepository.countByAlert(alert);
        long ack = recipientRepository.countByAlertAndStatus(alert, RecipientStatus.ACKNOWLEDGED);

        return new AlertResponseDto(
                alert.getId(),
                senderInfo,
                alert.getRoadName(),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getHeadingDeg(),
                spatialMathService.degreesToCompass(alert.getHeadingDeg()),
                alert.getAmbulanceType(),
                alert.getAmbulancePlate(),
                alert.getStatus().name(),
                recipient.getDistanceKm(),
                recipient.getBearingDeg(),
                connectingRoads,
                alert.getCreatedAt(),
                alert.getExpiresAt(),
                notified,
                ack
        );
    }

    private List<String> parseConnectingRoads(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
