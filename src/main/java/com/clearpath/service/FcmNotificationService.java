package com.clearpath.service;

import com.clearpath.model.AlertRecipient;
import com.clearpath.model.AmbulanceAlert;
import com.clearpath.model.DeviceToken;
import com.clearpath.repository.DeviceTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FcmNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FcmNotificationService.class);

    private final DeviceTokenRepository deviceTokenRepository;

    public FcmNotificationService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * Dispatches FCM push notification for high-priority emergency sirens.
     */
    public void sendEmergencyPushNotification(AlertRecipient recipient, AmbulanceAlert alert) {
        List<DeviceToken> tokens = deviceTokenRepository.findByOfficer(recipient.getOfficer());
        if (tokens.isEmpty()) {
            log.debug("No FCM device tokens found for officer: {}", recipient.getOfficer().getOfficerId());
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM MOCK DISPATCH] Firebase Admin SDK not configured with service credentials. " +
                     "Simulating background push notification to {} devices for Officer {}. " +
                     "Siren sound payload 'ambulance_siren', Channel 'clearpath_emergency', Alert ID: {}",
                     tokens.size(), recipient.getOfficer().getOfficerId(), alert.getId());
            return;
        }

        for (DeviceToken dt : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(dt.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle("EMERGENCY: Ambulance Approaching!")
                                .setBody(String.format("Spotted by %s on %s. Distance: %.2f km. Clear route ahead!",
                                        alert.getSenderOfficer().getName(),
                                        alert.getRoadName() != null ? alert.getRoadName() : "Main Road",
                                        recipient.getDistanceKm()))
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("ambulance_siren")
                                        .setChannelId("clearpath_emergency")
                                        .setPriority(AndroidNotification.Priority.MAX)
                                        .setClickAction("OPEN_CLEARPATH_ALERT")
                                        .build())
                                .build())
                        .putData("alertId", String.valueOf(alert.getId()))
                        .putData("senderOfficer", alert.getSenderOfficer().getName())
                        .putData("roadName", alert.getRoadName() != null ? alert.getRoadName() : "")
                        .putData("distanceKm", String.format("%.2f", recipient.getDistanceKm()))
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM push notification: {}", response);
            } catch (FirebaseMessagingException e) {
                log.warn("FCM dispatch error for token {}: {}", dt.getFcmToken(), e.getMessage());
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    deviceTokenRepository.delete(dt);
                }
            } catch (Exception e) {
                log.error("Unexpected error in FCM dispatch: {}", e.getMessage());
            }
        }
    }
}
