package com.clearpath.model;

import com.clearpath.model.enums.DutyStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "duty_sessions", indexes = {
    @Index(name = "idx_duty_officer_status", columnList = "officer_id, status")
})
public class DutySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private Officer officer;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DutyStatus status;

    public DutySession() {}

    public DutySession(Officer officer) {
        this.officer = officer;
        this.startedAt = Instant.now();
        this.status = DutyStatus.ON_DUTY;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Officer getOfficer() {
        return officer;
    }

    public void setOfficer(Officer officer) {
        this.officer = officer;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public DutyStatus getStatus() {
        return status;
    }

    public void setStatus(DutyStatus status) {
        this.status = status;
    }
}
