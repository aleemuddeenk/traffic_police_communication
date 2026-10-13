package com.clearpath.repository;

import com.clearpath.model.DutySession;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.DutyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DutySessionRepository extends JpaRepository<DutySession, Long> {
    Optional<DutySession> findTopByOfficerAndStatusOrderByStartedAtDesc(Officer officer, DutyStatus status);
    List<DutySession> findByOfficerOrderByStartedAtDesc(Officer officer);
    boolean existsByOfficerAndStatus(Officer officer, DutyStatus status);
}
