package com.clearpath.repository;

import com.clearpath.model.LocationPing;
import com.clearpath.model.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {

    Optional<LocationPing> findByOfficer(Officer officer);

    /**
     * Finds candidates within a lat/lon bounding box who are currently on duty and updated recently.
     */
    @Query("""
        SELECT lp FROM LocationPing lp
        WHERE lp.officer.id != :senderOfficerId
          AND lp.latitude BETWEEN :minLat AND :maxLat
          AND lp.longitude BETWEEN :minLon AND :maxLon
          AND lp.updatedAt >= :recentThreshold
          AND EXISTS (
              SELECT ds FROM DutySession ds
              WHERE ds.officer = lp.officer
                AND ds.status = com.clearpath.model.enums.DutyStatus.ON_DUTY
          )
    """)
    List<LocationPing> findOnDutyCandidatesInBoundingBox(
            @Param("senderOfficerId") Long senderOfficerId,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon,
            @Param("recentThreshold") Instant recentThreshold
    );

    /**
     * Find all on-duty officers with recent locations (used when bounding box is wide or fallback).
     */
    @Query("""
        SELECT lp FROM LocationPing lp
        WHERE lp.officer.id != :senderOfficerId
          AND EXISTS (
              SELECT ds FROM DutySession ds
              WHERE ds.officer = lp.officer
                AND ds.status = com.clearpath.model.enums.DutyStatus.ON_DUTY
          )
    """)
    List<LocationPing> findAllOnDutyOfficersExceptSender(@Param("senderOfficerId") Long senderOfficerId);
}
