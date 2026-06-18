package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitLocation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitLocationRepository extends JpaRepository<TransitLocation, Long> {

    Optional<TransitLocation> findTopByVehicleIdOrderByTimestampDesc(Long vehicleId);

    List<TransitLocation> findByVehicleIdAndTimestampGreaterThanEqualOrderByTimestampAsc(Long vehicleId, LocalDateTime from);

    List<TransitLocation> findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(Long vehicleId, LocalDateTime from, LocalDateTime to);
}
