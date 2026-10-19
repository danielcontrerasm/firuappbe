package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitSearchGroup;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitSearchGroupRepository extends JpaRepository<TransitSearchGroup, Long> {

    List<TransitSearchGroup> findByVehicleId(Long vehicleId);

    List<TransitSearchGroup> findByVehicleId(Long vehicleId, Pageable pageable);

    int countByVehicleIdAndStatusIgnoreCase(Long vehicleId, String status);
}
