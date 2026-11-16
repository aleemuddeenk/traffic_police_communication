package com.clearpath.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "civilians", indexes = {
    @Index(name = "idx_civilian_phone", columnList = "phone_number", unique = true)
})
public class Civilian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "ROLE_CIVILIAN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public Civilian() {}

    public Civilian(String phoneNumber, String displayName) {
        this.phoneNumber = phoneNumber;
        this.displayName = displayName != null ? displayName : "Civilian";
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
