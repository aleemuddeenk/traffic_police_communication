package com.clearpath.model;

import com.clearpath.model.enums.AlertStatus;
import jakarta.persistence.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;

@Entity
@Table(name = "ambulance_alerts", indexes = {
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_created_at", columnList = "created_at"),
    @Index(name = "idx_alert_sender", columnList = "sender_officer_id")
})
public class AmbulanceAlert {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sender_officer_id", nullable = false)
    private Officer senderOfficer;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "heading_deg", nullable = false)
    private Double headingDeg; // 0 - 360

    @Column(name = "road_name", length = 255)
    private String roadName;

    @Column(name = "ambulance_type", length = 50)
    private String ambulanceType; // e.g. "ALS", "BLS", "Patient Transport", "Unknown"

    @Column(name = "ambulance_plate", length = 50)
    private String ambulancePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.ACTIVE;

    @Column(name = "connecting_roads_json", columnDefinition = "TEXT")
    private String connectingRoadsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point point;

    public AmbulanceAlert() {}

    public AmbulanceAlert(Officer senderOfficer, Double latitude, Double longitude, Double headingDeg,
                          String roadName, String ambulanceType, String ambulancePlate, Instant expiresAt) {
        this.senderOfficer = senderOfficer;
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDeg = headingDeg;
        this.roadName = roadName;
        this.ambulanceType = ambulanceType;
        this.ambulancePlate = ambulancePlate;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(600); // 10 minutes default
        this.status = AlertStatus.ACTIVE;
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

    public Officer getSenderOfficer() {
        return senderOfficer;
    }

    public void setSenderOfficer(Officer senderOfficer) {
        this.senderOfficer = senderOfficer;
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

    public Double getHeadingDeg() {
        return headingDeg;
    }

    public void setHeadingDeg(Double headingDeg) {
        this.headingDeg = headingDeg;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public String getAmbulanceType() {
        return ambulanceType;
    }

    public void setAmbulanceType(String ambulanceType) {
        this.ambulanceType = ambulanceType;
    }

    public String getAmbulancePlate() {
        return ambulancePlate;
    }

    public void setAmbulancePlate(String ambulancePlate) {
        this.ambulancePlate = ambulancePlate;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getConnectingRoadsJson() {
        return connectingRoadsJson;
    }

    public void setConnectingRoadsJson(String connectingRoadsJson) {
        this.connectingRoadsJson = connectingRoadsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }
}
