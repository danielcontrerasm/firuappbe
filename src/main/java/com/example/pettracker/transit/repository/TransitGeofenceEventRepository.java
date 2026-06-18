package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitGeofenceEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitGeofenceEventRepository extends JpaRepository<TransitGeofenceEvent, Long> {

    List<TransitGeofenceEvent> findByVehicleIdAndCreatedAtBetween(Long vehicleId, Instant from, Instant to);
}
