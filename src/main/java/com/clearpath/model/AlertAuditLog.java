package com.clearpath.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_audit_logs", indexes = {
    @Index(name = "idx_audit_alert", columnList = "alert_id"),
    @Index(name = "idx_audit_actor", columnList = "actor_officer_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AlertAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "actor_officer_id", length = 50)
    private String actorOfficerId;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AlertAuditLog() {}

    public AlertAuditLog(Long alertId, String action, String actorOfficerId, String detailsJson) {
        this.alertId = alertId;
        this.action = action;
        this.actorOfficerId = actorOfficerId;
        this.detailsJson = detailsJson;
        this.timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActorOfficerId() {
        return actorOfficerId;
    }

    public void setActorOfficerId(String actorOfficerId) {
        this.actorOfficerId = actorOfficerId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
