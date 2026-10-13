package com.clearpath.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_token_officer", columnList = "officer_id")
})
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private Officer officer;

    @Column(name = "fcm_token", nullable = false, length = 500)
    private String fcmToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DeviceToken() {}

    public DeviceToken(Officer officer, String fcmToken) {
        this.officer = officer;
        this.fcmToken = fcmToken;
        this.updatedAt = Instant.now();
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

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
