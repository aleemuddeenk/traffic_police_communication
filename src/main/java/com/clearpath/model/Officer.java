package com.clearpath.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "officers", indexes = {
    @Index(name = "idx_officer_officer_id", columnList = "officerId", unique = true)
})
public class Officer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String officerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String rank;

    @Column(name = "badge_number", nullable = false, length = 50)
    private String badgeNumber;

    @Column(nullable = false, length = 100)
    private String station;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(length = 20)
    private String phone;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "ROLE_OFFICER";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Officer() {}

    public Officer(String officerId, String name, String rank, String badgeNumber,
                   String station, String district, String phone, String photoUrl, String passwordHash) {
        this.officerId = officerId;
        this.name = name;
        this.rank = rank;
        this.badgeNumber = badgeNumber;
        this.station = station;
        this.district = district;
        this.phone = phone;
        this.photoUrl = photoUrl;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOfficerId() {
        return officerId;
    }

    public void setOfficerId(String officerId) {
        this.officerId = officerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getBadgeNumber() {
        return badgeNumber;
    }

    public void setBadgeNumber(String badgeNumber) {
        this.badgeNumber = badgeNumber;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
