package com.clearpath.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;

@Entity
@Table(name = "location_pings", indexes = {
    @Index(name = "idx_ping_officer", columnList = "officer_id", unique = true),
    @Index(name = "idx_ping_lat_lon", columnList = "latitude, longitude"),
    @Index(name = "idx_ping_updated_at", columnList = "updated_at")
})
public class LocationPing {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "officer_id", nullable = false, unique = true)
    private Officer officer;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column
    private Double heading; // 0 - 360 degrees

    @Column
    private Double speed; // meters per second or km/h

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point point;

    public LocationPing() {}

    public LocationPing(Officer officer, Double latitude, Double longitude, Double heading, Double speed) {
        this.officer = officer;
        this.latitude = latitude;
        this.longitude = longitude;
        this.heading = heading;
        this.speed = speed;
        this.updatedAt = Instant.now();
        if (latitude != null && longitude != null) {
            this.point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
            this.point.setSRID(4326);
        }
    }

    public void update(Double latitude, Double longitude, Double heading, Double speed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.heading = heading;
        this.speed = speed;
        this.updatedAt = Instant.now();
        if (latitude != null && longitude != null) {
            this.point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
            this.point.setSRID(4326);
        }
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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }
}
