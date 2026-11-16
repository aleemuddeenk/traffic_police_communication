package com.clearpath.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory OTP service for civilian phone verification.
 * In production, replace sendOtp() with real SMS gateway (Twilio, MSG91, etc.).
 * OTPs expire in 5 minutes. Max 3 attempts per OTP before lockout.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final long OTP_TTL_SECONDS = 300; // 5 minutes
    private static final int MAX_ATTEMPTS = 3;
    private static final Random RANDOM = new Random();

    private record OtpEntry(String otp, Instant expiresAt, int attempts) {}

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    /**
     * Generates and "sends" (logs) an OTP for the given phone number.
     * Returns the OTP for demo environments where you need to display it.
     */
    public String sendOtp(String phoneNumber) {
        String normalised = normalise(phoneNumber);
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpEntry entry = new OtpEntry(otp, Instant.now().plusSeconds(OTP_TTL_SECONDS), 0);
        store.put(normalised, entry);

        // --- Replace this log line with real SMS in production ---
        log.info("[OTP-DEMO] Phone {} OTP: {}", normalised, otp);
        // ---------------------------------------------------------

        return otp; // returned so the demo UI can show it
    }

    /**
     * Verifies the OTP. Returns true on success, false otherwise.
     */
    public boolean verifyOtp(String phoneNumber, String submittedOtp) {
        String normalised = normalise(phoneNumber);
        OtpEntry entry = store.get(normalised);

        if (entry == null) {
            log.warn("OTP verify attempt for unknown phone: {}", normalised);
            return false;
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(normalised);
            log.warn("OTP expired for phone: {}", normalised);
            return false;
        }

        if (entry.attempts() >= MAX_ATTEMPTS) {
            store.remove(normalised);
            log.warn("OTP max attempts exceeded for phone: {}", normalised);
            return false;
        }

        if (!entry.otp().equals(submittedOtp.trim())) {
            // Increment attempt counter
            store.put(normalised, new OtpEntry(entry.otp(), entry.expiresAt(), entry.attempts() + 1));
            log.warn("OTP mismatch for phone: {} (attempt {})", normalised, entry.attempts() + 1);
            return false;
        }

        store.remove(normalised);
        log.info("OTP verified successfully for phone: {}", normalised);
        return true;
    }

    /** Clean up expired entries every 10 minutes. */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private String normalise(String phone) {
        // Keep digits and leading + only
        return phone == null ? "" : phone.trim().replaceAll("[^+\\d]", "");
    }
}
