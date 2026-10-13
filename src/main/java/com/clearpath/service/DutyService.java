package com.clearpath.service;

import com.clearpath.model.DutySession;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.DutyStatus;
import com.clearpath.repository.DutySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class DutyService {

    private final DutySessionRepository dutySessionRepository;
    private final AuditService auditService;

    public DutyService(DutySessionRepository dutySessionRepository, AuditService auditService) {
        this.dutySessionRepository = dutySessionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DutySession startDuty(Officer officer) {
        // Close any dangling sessions
        dutySessionRepository.findTopByOfficerAndStatusOrderByStartedAtDesc(officer, DutyStatus.ON_DUTY)
                .ifPresent(session -> {
                    session.setStatus(DutyStatus.OFF_DUTY);
                    session.setEndedAt(Instant.now());
                    dutySessionRepository.save(session);
                });

        DutySession newSession = new DutySession(officer);
        DutySession saved = dutySessionRepository.save(newSession);
        auditService.logAction(null, "DUTY_STARTED", officer.getOfficerId(), "Officer started duty");
        return saved;
    }

    @Transactional
    public void endDuty(Officer officer) {
        dutySessionRepository.findTopByOfficerAndStatusOrderByStartedAtDesc(officer, DutyStatus.ON_DUTY)
                .ifPresent(session -> {
                    session.setStatus(DutyStatus.OFF_DUTY);
                    session.setEndedAt(Instant.now());
                    dutySessionRepository.save(session);
                    auditService.logAction(null, "DUTY_ENDED", officer.getOfficerId(), "Officer ended duty");
                });
    }

    public boolean isOnDuty(Officer officer) {
        return dutySessionRepository.existsByOfficerAndStatus(officer, DutyStatus.ON_DUTY);
    }

    public Optional<DutySession> getActiveSession(Officer officer) {
        return dutySessionRepository.findTopByOfficerAndStatusOrderByStartedAtDesc(officer, DutyStatus.ON_DUTY);
    }
}
