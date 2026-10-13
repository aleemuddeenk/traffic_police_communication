package com.clearpath.repository;

import com.clearpath.model.AlertAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertAuditLogRepository extends JpaRepository<AlertAuditLog, Long> {
    List<AlertAuditLog> findByAlertIdOrderByTimestampDesc(Long alertId);
    List<AlertAuditLog> findTop50ByOrderByTimestampDesc();
}
