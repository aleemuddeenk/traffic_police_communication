package com.clearpath.repository;

import com.clearpath.model.DeviceToken;
import com.clearpath.model.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByOfficer(Officer officer);
    Optional<DeviceToken> findByOfficerAndFcmToken(Officer officer, String fcmToken);
    void deleteByFcmToken(String fcmToken);
}
