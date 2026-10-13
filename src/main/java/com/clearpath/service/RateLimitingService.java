package com.clearpath.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    // Officer ID -> Last trigger timestamp
    private final Map<String, Instant> lastTriggerMap = new ConcurrentHashMap<>();
    private static final long COOLDOWN_SECONDS = 3; // 3-second cooldown between emergency triggers per officer

    /**
     * Checks if the officer is allowed to trigger an alert.
     *
     * @return true if allowed, false if rate limited
     */
    public boolean checkAndAcquireAlertTrigger(String officerId) {
        Instant now = Instant.now();
        Instant last = lastTriggerMap.get(officerId);

        if (last != null && now.isBefore(last.plusSeconds(COOLDOWN_SECONDS))) {
            return false;
        }

        lastTriggerMap.put(officerId, now);
        return true;
    }
}
