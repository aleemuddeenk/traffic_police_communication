package com.clearpath.service;

import com.clearpath.model.AlertAuditLog;
import com.clearpath.repository.AlertAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AlertAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AlertAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logAction(Long alertId, String action, String actorOfficerId, Object details) {
        String detailsJson = null;
        try {
            if (details != null) {
                detailsJson = objectMapper.writeValueAsString(details);
            }
        } catch (Exception e) {
            detailsJson = String.valueOf(details);
        }

        AlertAuditLog logEntry = new AlertAuditLog(alertId, action, actorOfficerId, detailsJson);
        auditLogRepository.save(logEntry);
        log.info("[AUDIT] Action: {} | Officer: {} | AlertId: {} | Details: {}", action, actorOfficerId, alertId, detailsJson);
    }
}
