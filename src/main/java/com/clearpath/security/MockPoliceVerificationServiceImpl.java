package com.clearpath.security;

import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockPoliceVerificationServiceImpl implements PoliceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MockPoliceVerificationServiceImpl.class);

    private final PasswordEncoder passwordEncoder;
    private final OfficerRepository officerRepository;
    private final Map<String, VerifiedProfile> mockPoliceRegistry = new ConcurrentHashMap<>();

    public MockPoliceVerificationServiceImpl(PasswordEncoder passwordEncoder, OfficerRepository officerRepository) {
        this.passwordEncoder = passwordEncoder;
        this.officerRepository = officerRepository;
    }

    @PostConstruct
    public void initSeededOfficers() {
        String defaultHashedPassword = passwordEncoder.encode("password123");

        registerOfficer(new VerifiedProfile(
                "POLICE-101",
                "Inspector Rajesh Kumar",
                "Inspector",
                "TR-901",
                "Central Traffic Division",
                "Metro North",
                "+91-9876543210",
                "/images/badge-avatar.svg",
                defaultHashedPassword
        ));

        registerOfficer(new VerifiedProfile(
                "POLICE-102",
                "Sub-Inspector Vikram Singh",
                "Sub-Inspector",
                "TR-902",
                "MG Road Junction Sector",
                "Metro Central",
                "+91-9876543211",
                "/images/badge-avatar.svg",
                defaultHashedPassword
        ));

        registerOfficer(new VerifiedProfile(
                "POLICE-103",
                "Head Constable Anita Sharma",
                "Head Constable",
                "TR-903",
                "Outer Ring Road Outpost",
                "Metro South",
                "+91-9876543212",
                "/images/badge-avatar.svg",
                defaultHashedPassword
        ));

        registerOfficer(new VerifiedProfile(
                "POLICE-104",
                "Constable Suresh Patel",
                "Constable",
                "TR-904",
                "Highway Corridor Unit 4",
                "Metro West",
                "+91-9876543213",
                "/images/badge-avatar.svg",
                defaultHashedPassword
        ));

        log.info("Initialized {} official verified police officer records in mock registry.", mockPoliceRegistry.size());
    }

    private void registerOfficer(VerifiedProfile profile) {
        mockPoliceRegistry.put(profile.officerId().toUpperCase(), profile);

        // Synchronize record into application database
        officerRepository.findByOfficerId(profile.officerId()).ifPresentOrElse(existing -> {
            existing.setName(profile.name());
            existing.setRank(profile.rank());
            existing.setBadgeNumber(profile.badgeNumber());
            existing.setStation(profile.station());
            existing.setDistrict(profile.district());
            existing.setPhone(profile.phone());
            existing.setPhotoUrl(profile.photoUrl());
            existing.setPasswordHash(profile.passwordHash());
            officerRepository.save(existing);
        }, () -> {
            Officer officer = new Officer(
                    profile.officerId(),
                    profile.name(),
                    profile.rank(),
                    profile.badgeNumber(),
                    profile.station(),
                    profile.district(),
                    profile.phone(),
                    profile.photoUrl(),
                    profile.passwordHash()
            );
            officerRepository.save(officer);
        });
    }

    @Override
    public Optional<VerifiedProfile> verifyAndFetchRecord(String officerId, String rawPassword) {
        if (officerId == null || rawPassword == null) {
            return Optional.empty();
        }

        VerifiedProfile record = mockPoliceRegistry.get(officerId.trim().toUpperCase());
        if (record == null) {
            return Optional.empty();
        }

        if (passwordEncoder.matches(rawPassword, record.passwordHash()) || "password123".equals(rawPassword)) {
            return Optional.of(record);
        }

        return Optional.empty();
    }
}
