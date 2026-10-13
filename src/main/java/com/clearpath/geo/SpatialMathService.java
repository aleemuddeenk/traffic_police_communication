package com.clearpath.geo;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class SpatialMathService {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    public static final double MAX_TARGET_RADIUS_KM = 3.0;
    public static final double PROXIMITY_OVERRIDE_KM = 0.2; // 200 meters
    public static final double MAX_FORWARD_ANGLE_DEG = 60.0; // +/- 60 degrees

    private static final Map<String, Double> COMPASS_TO_DEGREES = Map.of(
            "N", 0.0,
            "NE", 45.0,
            "E", 90.0,
            "SE", 135.0,
            "S", 180.0,
            "SW", 225.0,
            "W", 270.0,
            "NW", 315.0
    );

    /**
     * Calculates the great-circle distance between two points using the Haversine formula.
     *
     * @return distance in kilometers
     */
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates the initial forward bearing (azimuth) from point 1 to point 2.
     *
     * @return bearing in degrees [0, 360)
     */
    public double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLon) * Math.cos(rLat2);
        double x = Math.cos(rLat1) * Math.sin(rLat2)
                - Math.sin(rLat1) * Math.cos(rLat2) * Math.cos(dLon);

        double initialBearingRad = Math.atan2(y, x);
        double initialBearingDeg = Math.toDegrees(initialBearingRad);

        return (initialBearingDeg + 360.0) % 360.0;
    }

    /**
     * Computes the absolute minimum angular difference between two angles in degrees [0, 180].
     */
    public double calculateAngularDifference(double angle1, double angle2) {
        double diff = Math.abs(angle1 - angle2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    /**
     * Checks if a target officer is "ahead" of the ambulance according to the targeting rules:
     * 1. Distance must be within max radius (3.0 km).
     * 2. If within 200 meters (0.2 km), always considered ahead (close-proximity intersection safety).
     * 3. Otherwise, the bearing from the ambulance to the officer must be within +/- 60 degrees
     *    of the ambulance's facing direction.
     *
     * @param ambulanceLat ambulance latitude
     * @param ambulanceLon ambulance longitude
     * @param ambulanceHeading ambulance heading in degrees (0 - 360)
     * @param officerLat officer latitude
     * @param officerLon officer longitude
     * @return TargetResult containing distanceKm, bearingDeg, angularDiffDeg, and whether eligible
     */
    public TargetCheckResult evaluateTarget(double ambulanceLat, double ambulanceLon, double ambulanceHeading,
                                            double officerLat, double officerLon) {
        double distanceKm = calculateDistanceKm(ambulanceLat, ambulanceLon, officerLat, officerLon);

        if (distanceKm > MAX_TARGET_RADIUS_KM) {
            return new TargetCheckResult(false, distanceKm, 0.0, 0.0, "Out of 3 km radius");
        }

        // Close proximity safety: within 200 meters, always alert
        if (distanceKm <= PROXIMITY_OVERRIDE_KM) {
            double bearing = calculateBearing(ambulanceLat, ambulanceLon, officerLat, officerLon);
            double angleDiff = calculateAngularDifference(ambulanceHeading, bearing);
            return new TargetCheckResult(true, distanceKm, bearing, angleDiff, "Within 200m safety proximity zone");
        }

        double bearing = calculateBearing(ambulanceLat, ambulanceLon, officerLat, officerLon);
        double angleDiff = calculateAngularDifference(ambulanceHeading, bearing);

        boolean isAhead = angleDiff <= MAX_FORWARD_ANGLE_DEG;
        String reason = isAhead ? "Within +/- 60 deg forward cone" : "Officer is behind ambulance trajectory";

        return new TargetCheckResult(isAhead, distanceKm, bearing, angleDiff, reason);
    }

    /**
     * Converts a compass direction code (e.g. "N", "NE", "E") to degrees.
     */
    public double compassToDegrees(String compassCode) {
        if (compassCode == null) {
            return 0.0;
        }
        return COMPASS_TO_DEGREES.getOrDefault(compassCode.trim().toUpperCase(Locale.ROOT), 0.0);
    }

    /**
     * Converts a heading in degrees [0, 360) to the nearest 8-point compass abbreviation.
     */
    public String degreesToCompass(double degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double normalized = (degrees % 360.0 + 360.0) % 360.0;
        int index = (int) Math.round(normalized / 45.0) % 8;
        return directions[index];
    }

    /**
     * Calculates a bounding box in degrees for a given center point and radius in kilometers.
     */
    public BoundingBox calculateBoundingBox(double lat, double lon, double radiusKm) {
        double latDelta = radiusKm / 111.32;
        double cosLat = Math.cos(Math.toRadians(lat));
        if (Math.abs(cosLat) < 1e-6) {
            cosLat = 1e-6; // guard against division by zero at poles
        }
        double lonDelta = radiusKm / (111.32 * cosLat);

        return new BoundingBox(
                lat - latDelta,
                lat + latDelta,
                lon - lonDelta,
                lon + lonDelta
        );
    }

    public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {}

    public record TargetCheckResult(
            boolean isEligible,
            double distanceKm,
            double bearingDeg,
            double angularDiffDeg,
            String reason
    ) {}
}
