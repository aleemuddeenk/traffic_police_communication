package com.clearpath.repository;

import com.clearpath.model.AlertRecipient;
import com.clearpath.model.AmbulanceAlert;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.RecipientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRecipientRepository extends JpaRepository<AlertRecipient, Long> {

    List<AlertRecipient> findByAlert(AmbulanceAlert alert);

    List<AlertRecipient> findByAlertId(Long alertId);

    Optional<AlertRecipient> findByAlertAndOfficer(AmbulanceAlert alert, Officer officer);

    Optional<AlertRecipient> findByAlertIdAndOfficerId(Long alertId, Long officerId);

    long countByAlert(AmbulanceAlert alert);

    long countByAlertAndStatus(AmbulanceAlert alert, RecipientStatus status);

    List<AlertRecipient> findByOfficerOrderByNotifiedAtDesc(Officer officer);
}
