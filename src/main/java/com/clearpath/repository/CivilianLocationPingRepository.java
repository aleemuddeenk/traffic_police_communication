package com.clearpath.repository;

import com.clearpath.model.Civilian;
import com.clearpath.model.CivilianLocationPing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CivilianLocationPingRepository extends JpaRepository<CivilianLocationPing, Long> {

    Optional<CivilianLocationPing> findByCivilian(Civilian civilian);

    /**
     * Finds active civilians within a lat/lon bounding box updated recently.
     */
    @Query("""
        SELECT clp FROM CivilianLocationPing clp
        WHERE clp.active = true
          AND clp.latitude BETWEEN :minLat AND :maxLat
          AND clp.longitude BETWEEN :minLon AND :maxLon
          AND clp.updatedAt >= :recentThreshold
    """)
    List<CivilianLocationPing> findActiveCiviliansInBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon,
            @Param("recentThreshold") Instant recentThreshold
    );

    /**
     * Fallback: all active civilians (for demo/tests with wide or no spatial filter).
     */
    @Query("SELECT clp FROM CivilianLocationPing clp WHERE clp.active = true")
    List<CivilianLocationPing> findAllActiveCivilians();
}
