package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitScheduledGeofence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitScheduledGeofenceRepository extends JpaRepository<TransitScheduledGeofence, Long> {

    List<TransitScheduledGeofence> findByVehicleIdAndEnabledTrue(Long vehicleId);

    List<TransitScheduledGeofence> findByVehicleId(Long vehicleId);
}
