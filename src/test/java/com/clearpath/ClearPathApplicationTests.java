package com.clearpath;

import com.clearpath.dto.CreateAlertRequest;
import com.clearpath.dto.LocationPingDto;
import com.clearpath.model.Officer;
import com.clearpath.model.enums.DutyStatus;
import com.clearpath.repository.OfficerRepository;
import com.clearpath.service.AlertService;
import com.clearpath.service.DutyService;
import com.clearpath.service.LocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClearPathApplicationTests {

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private DutyService dutyService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private AlertService alertService;

    @Test
    @DisplayName("Context loads and verified sample officers are seeded")
    void testContextLoadsAndOfficersSeeded() {
        assertTrue(officerRepository.count() >= 3, "Sample officers should be seeded");
        assertTrue(officerRepository.existsByOfficerId("POLICE-101"));
        assertTrue(officerRepository.existsByOfficerId("POLICE-102"));
        assertTrue(officerRepository.existsByOfficerId("POLICE-103"));
    }

    @Test
    @DisplayName("Duty session start and end lifecycle updates state correctly")
    void testDutyLifecycle() {
        Officer officer = officerRepository.findByOfficerId("POLICE-101").orElseThrow();

        dutyService.startDuty(officer);
        assertTrue(dutyService.isOnDuty(officer));

        dutyService.endDuty(officer);
        assertFalse(dutyService.isOnDuty(officer));
    }

    @Test
    @DisplayName("Alert targeting: only ahead officers receive alert, backward officers are strictly excluded")
    void testAlertTargetingForwardOnly() {
        Officer sender = officerRepository.findByOfficerId("POLICE-101").orElseThrow();
        Officer aheadOfficer = officerRepository.findByOfficerId("POLICE-102").orElseThrow();
        Officer behindOfficer = officerRepository.findByOfficerId("POLICE-103").orElseThrow();

        // Put all three officers on duty
        dutyService.startDuty(sender);
        dutyService.startDuty(aheadOfficer);
        dutyService.startDuty(behindOfficer);

        // Position sender at origin (12.9716, 77.5946)
        locationService.recordLocation(sender, new LocationPingDto(12.9716, 77.5946, 0.0, 0.0));

        // Position aheadOfficer ~1 km North (12.9806, 77.5946)
        locationService.recordLocation(aheadOfficer, new LocationPingDto(12.9806, 77.5946, 0.0, 0.0));

        // Position behindOfficer ~1 km South (12.9626, 77.5946)
        locationService.recordLocation(behindOfficer, new LocationPingDto(12.9626, 77.5946, 0.0, 0.0));

        // Sender spots ambulance facing North (0 deg)
        CreateAlertRequest request = new CreateAlertRequest(
                12.9716,
                77.5946,
                0.0,
                "N",
                "Advanced Life Support (ALS)",
                "KA-01-EA-108"
        );

        var alertResponse = alertService.triggerAlert(sender, request);
        assertNotNull(alertResponse);
        assertEquals(1, alertResponse.notifiedCount(), "Only 1 officer (aheadOfficer) should be notified");

        // Verify active alerts query for aheadOfficer vs behindOfficer
        var aheadAlerts = alertService.getActiveAlertsForOfficer(aheadOfficer);
        assertFalse(aheadAlerts.isEmpty(), "Ahead officer must see active alert");

        var behindAlerts = alertService.getActiveAlertsForOfficer(behindOfficer);
        assertTrue(behindAlerts.isEmpty(), "Behind officer must NOT receive or see the alert");
    }
}
