package com.clearpath.repository;

import com.clearpath.model.AmbulanceAlert;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AmbulanceAlertRepository extends JpaRepository<AmbulanceAlert, Long> {

    List<AmbulanceAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    List<AmbulanceAlert> findBySenderOfficerOrderByCreatedAtDesc(Officer senderOfficer);

    Optional<AmbulanceAlert> findTopBySenderOfficerAndStatusOrderByCreatedAtDesc(Officer senderOfficer, AlertStatus status);

    List<AmbulanceAlert> findByStatusAndExpiresAtBefore(AlertStatus status, Instant now);
}
