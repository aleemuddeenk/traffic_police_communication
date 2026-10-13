package com.clearpath.security;

import java.util.Optional;

public interface PoliceVerificationService {

    /**
     * Verifies the officer credentials against the official police database / gateway.
     *
     * @param officerId Official Police Officer Identifier
     * @param password Secret password or OTP
     * @return VerifiedProfile containing official record if authenticated, empty otherwise
     */
    Optional<VerifiedProfile> verifyAndFetchRecord(String officerId, String password);

    record VerifiedProfile(
            String officerId,
            String name,
            String rank,
            String badgeNumber,
            String station,
            String district,
            String phone,
            String photoUrl,
            String passwordHash
    ) {}
}
