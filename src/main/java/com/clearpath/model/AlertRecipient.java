package com.clearpath.model;

import com.clearpath.model.enums.RecipientStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_recipients", indexes = {
    @Index(name = "idx_recip_alert", columnList = "alert_id"),
    @Index(name = "idx_recip_officer", columnList = "officer_id"),
    @Index(name = "idx_recip_status", columnList = "status")
})
public class AlertRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private AmbulanceAlert alert;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private Officer officer;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Column(name = "bearing_deg", nullable = false)
    private Double bearingDeg;

    @Column(name = "is_ahead", nullable = false)
    private Boolean isAhead = true;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipientStatus status = RecipientStatus.NOTIFIED;

    public AlertRecipient() {}

    public AlertRecipient(AmbulanceAlert alert, Officer officer, Double distanceKm, Double bearingDeg, Boolean isAhead) {
        this.alert = alert;
        this.officer = officer;
        this.distanceKm = distanceKm;
        this.bearingDeg = bearingDeg;
        this.isAhead = isAhead;
        this.notifiedAt = Instant.now();
        this.status = RecipientStatus.NOTIFIED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AmbulanceAlert getAlert() {
        return alert;
    }

    public void setAlert(AmbulanceAlert alert) {
        this.alert = alert;
    }

    public Officer getOfficer() {
        return officer;
    }

    public void setOfficer(Officer officer) {
        this.officer = officer;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Double getBearingDeg() {
        return bearingDeg;
    }

    public void setBearingDeg(Double bearingDeg) {
        this.bearingDeg = bearingDeg;
    }

    public Boolean getIsAhead() {
        return isAhead;
    }

    public void setIsAhead(Boolean isAhead) {
        this.isAhead = isAhead;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public RecipientStatus getStatus() {
        return status;
    }

    public void setStatus(RecipientStatus status) {
        this.status = status;
    }
}
