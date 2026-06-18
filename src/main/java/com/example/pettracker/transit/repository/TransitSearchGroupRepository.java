package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitSearchGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitSearchGroupRepository extends JpaRepository<TransitSearchGroup, Long> {

    List<TransitSearchGroup> findByVehicleId(Long vehicleId);

    int countByVehicleIdAndStatusIgnoreCase(Long vehicleId, String status);
}
