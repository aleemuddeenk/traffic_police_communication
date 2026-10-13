package com.clearpath.service;

import com.clearpath.dto.LocationPingDto;
import com.clearpath.model.LocationPing;
import com.clearpath.model.Officer;
import com.clearpath.repository.LocationPingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LocationService {

    private final LocationPingRepository locationPingRepository;
    private final DutyService dutyService;

    public LocationService(LocationPingRepository locationPingRepository, DutyService dutyService) {
        this.locationPingRepository = locationPingRepository;
        this.dutyService = dutyService;
    }

    @Transactional
    public LocationPing recordLocation(Officer officer, LocationPingDto dto) {
        if (!dutyService.isOnDuty(officer)) {
            throw new IllegalStateException("Officer is currently OFF DUTY. Must start duty before transmitting location.");
        }

        Optional<LocationPing> existing = locationPingRepository.findByOfficer(officer);
        LocationPing ping;
        if (existing.isPresent()) {
            ping = existing.get();
            ping.update(dto.latitude(), dto.longitude(), dto.heading(), dto.speed());
        } else {
            ping = new LocationPing(officer, dto.latitude(), dto.longitude(), dto.heading(), dto.speed());
        }

        return locationPingRepository.save(ping);
    }

    public Optional<LocationPing> getOfficerLocation(Officer officer) {
        return locationPingRepository.findByOfficer(officer);
    }
}
