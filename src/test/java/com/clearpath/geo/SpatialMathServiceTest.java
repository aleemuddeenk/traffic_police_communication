package com.clearpath.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpatialMathServiceTest {

    private SpatialMathService spatialMathService;

    // Base location: MG Road Corridor (12.9716, 77.5946)
    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LON = 77.5946;

    @BeforeEach
    void setUp() {
        spatialMathService = new SpatialMathService();
    }

    @Test
    @DisplayName("Should return 0 km for identical coordinates")
    void testZeroDistance() {
        double dist = spatialMathService.calculateDistanceKm(BASE_LAT, BASE_LON, BASE_LAT, BASE_LON);
        assertEquals(0.0, dist, 0.0001);
    }

    @Test
    @DisplayName("Should calculate accurate distance for points ~1 km apart")
    void testAccurateDistanceOneKm() {
        // Moving roughly 0.009 degrees North is ~1.0 km
        double targetLat = BASE_LAT + 0.009;
        double dist = spatialMathService.calculateDistanceKm(BASE_LAT, BASE_LON, targetLat, BASE_LON);

        assertTrue(dist >= 0.95 && dist <= 1.05, "Expected ~1.0 km distance, got: " + dist);
    }

    @Test
    @DisplayName("Should calculate forward bearings correctly across cardinal directions")
    void testCalculateBearing() {
        // Directly North
        double bearingNorth = spatialMathService.calculateBearing(BASE_LAT, BASE_LON, BASE_LAT + 0.01, BASE_LON);
        assertEquals(0.0, bearingNorth, 1.0);

        // Directly East
        double bearingEast = spatialMathService.calculateBearing(BASE_LAT, BASE_LON, BASE_LAT, BASE_LON + 0.01);
        assertEquals(90.0, bearingEast, 1.0);

        // Directly South
        double bearingSouth = spatialMathService.calculateBearing(BASE_LAT, BASE_LON, BASE_LAT - 0.01, BASE_LON);
        assertEquals(180.0, bearingSouth, 1.0);

        // Directly West
        double bearingWest = spatialMathService.calculateBearing(BASE_LAT, BASE_LON, BASE_LAT, BASE_LON - 0.01);
        assertEquals(270.0, bearingWest, 1.0);
    }

    @Test
    @DisplayName("Should calculate minimal angular difference across 0/360 wrap-around")
    void testAngularDifference() {
        assertEquals(0.0, spatialMathService.calculateAngularDifference(45.0, 45.0), 0.001);
        assertEquals(20.0, spatialMathService.calculateAngularDifference(10.0, 350.0), 0.001);
        assertEquals(20.0, spatialMathService.calculateAngularDifference(350.0, 10.0), 0.001);
        assertEquals(60.0, spatialMathService.calculateAngularDifference(0.0, 60.0), 0.001);
        assertEquals(60.0, spatialMathService.calculateAngularDifference(0.0, 300.0), 0.001);
        assertEquals(180.0, spatialMathService.calculateAngularDifference(90.0, 270.0), 0.001);
    }

    @Test
    @DisplayName("Should accept officer directly ahead on forward trajectory (< 3 km, angle = 0)")
    void testTargetDirectlyAheadAccepted() {
        double ambulanceHeading = 0.0; // Facing North
        double officerLat = BASE_LAT + 0.01; // ~1.1 km North
        double officerLon = BASE_LON;

        SpatialMathService.TargetCheckResult result = spatialMathService.evaluateTarget(
                BASE_LAT, BASE_LON, ambulanceHeading, officerLat, officerLon);

        assertTrue(result.isEligible(), "Officer directly ahead should be eligible");
        assertTrue(result.distanceKm() < 3.0, "Distance must be within 3 km");
        assertEquals(0.0, result.angularDiffDeg(), 1.0);
    }

    @Test
    @DisplayName("Should accept officer within +/- 60 degree forward cone")
    void testTargetWithinForwardConeAccepted() {
        double ambulanceHeading = 0.0; // Facing North
        // 45 degrees Northeast, ~1.5 km away
        double officerLat = BASE_LAT + 0.01;
        double officerLon = BASE_LON + 0.01;

        SpatialMathService.TargetCheckResult result = spatialMathService.evaluateTarget(
                BASE_LAT, BASE_LON, ambulanceHeading, officerLat, officerLon);

        assertTrue(result.isEligible(), "Officer within 60 deg cone must be eligible");
        assertTrue(result.angularDiffDeg() <= 60.0);
    }

    @Test
    @DisplayName("Should strictly REJECT officer behind ambulance (> 60 deg and > 200m away)")
    void testTargetBehindAmbulanceRejected() {
        double ambulanceHeading = 0.0; // Facing North
        double officerLat = BASE_LAT - 0.01; // ~1.1 km South (BEHIND)
        double officerLon = BASE_LON;

        SpatialMathService.TargetCheckResult result = spatialMathService.evaluateTarget(
                BASE_LAT, BASE_LON, ambulanceHeading, officerLat, officerLon);

        assertFalse(result.isEligible(), "Officer behind ambulance must NOT be notified");
        assertTrue(result.angularDiffDeg() > 60.0);
        assertTrue(result.reason().contains("behind"));
    }

    @Test
    @DisplayName("Should ACCEPT officer within 200m safety zone even if behind (close proximity rule)")
    void testTargetWithin200mSafetyOverride() {
        double ambulanceHeading = 0.0; // Facing North
        // Only 50 meters behind
        double officerLat = BASE_LAT - 0.00045; // ~50m South
        double officerLon = BASE_LON;

        SpatialMathService.TargetCheckResult result = spatialMathService.evaluateTarget(
                BASE_LAT, BASE_LON, ambulanceHeading, officerLat, officerLon);

        assertTrue(result.isEligible(), "Officer within 200m must be alerted for immediate intersection safety");
        assertTrue(result.distanceKm() <= 0.2);
    }

    @Test
    @DisplayName("Should REJECT officer beyond 3 km even if directly ahead")
    void testTargetBeyond3KmRejected() {
        double ambulanceHeading = 0.0; // Facing North
        double officerLat = BASE_LAT + 0.04; // ~4.4 km North
        double officerLon = BASE_LON;

        SpatialMathService.TargetCheckResult result = spatialMathService.evaluateTarget(
                BASE_LAT, BASE_LON, ambulanceHeading, officerLat, officerLon);

        assertFalse(result.isEligible(), "Officer beyond 3 km must be rejected");
        assertTrue(result.distanceKm() > 3.0);
    }

    @Test
    @DisplayName("Should convert 8 compass directions accurately")
    void testCompassConversions() {
        assertEquals(0.0, spatialMathService.compassToDegrees("N"));
        assertEquals(45.0, spatialMathService.compassToDegrees("NE"));
        assertEquals(90.0, spatialMathService.compassToDegrees("E"));
        assertEquals(180.0, spatialMathService.compassToDegrees("S"));
        assertEquals(270.0, spatialMathService.compassToDegrees("W"));

        assertEquals("N", spatialMathService.degreesToCompass(358.0));
        assertEquals("N", spatialMathService.degreesToCompass(5.0));
        assertEquals("NE", spatialMathService.degreesToCompass(45.0));
        assertEquals("E", spatialMathService.degreesToCompass(88.0));
        assertEquals("S", spatialMathService.degreesToCompass(182.0));
        assertEquals("W", spatialMathService.degreesToCompass(271.0));
        assertEquals("NW", spatialMathService.degreesToCompass(315.0));
    }
}
