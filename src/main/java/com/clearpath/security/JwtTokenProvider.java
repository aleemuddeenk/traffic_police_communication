package com.clearpath.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${clearpath.jwt.secret:ClearPathPoliceSecretKeySuperSecureAndLongEnoughForHmacSha256_2026!}") String secret,
            @Value("${clearpath.jwt.expiration-ms:86400000}") long validityInMilliseconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
    }

    /** Generate a token that embeds the user's role (ROLE_OFFICER or ROLE_CIVILIAN). */
    public String generateToken(String subject, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    /** Backward-compat overload: defaults to ROLE_OFFICER. */
    public String generateToken(String officerId) {
        return generateToken(officerId, "ROLE_OFFICER");
    }

    public String getSubjectFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /** Kept for backward compat with existing code using getOfficerIdFromToken. */
    public String getOfficerIdFromToken(String token) {
        return getSubjectFromToken(token);
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        String role = claims.get("role", String.class);
        return role != null ? role : "ROLE_OFFICER"; // default for legacy tokens
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
