package com.clearpath.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "civilian_location_pings", indexes = {
    @Index(name = "idx_civ_ping_civilian", columnList = "civilian_id", unique = true),
    @Index(name = "idx_civ_ping_lat_lon", columnList = "latitude, longitude"),
    @Index(name = "idx_civ_ping_updated_at", columnList = "updated_at")
})
public class CivilianLocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "civilian_id", nullable = false, unique = true)
    private Civilian civilian;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column
    private Double heading;

    @Column
    private Double speed;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CivilianLocationPing() {}

    public CivilianLocationPing(Civilian civilian, Double latitude, Double longitude, Double heading, Double speed) {
        this.civilian = civilian;
        this.latitude = latitude;
        this.longitude = longitude;
        this.heading = heading;
        this.speed = speed;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void update(Double latitude, Double longitude, Double heading, Double speed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.heading = heading;
        this.speed = speed;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Civilian getCivilian() { return civilian; }
    public void setCivilian(Civilian civilian) { this.civilian = civilian; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
