package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitVehicle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitVehicleRepository extends JpaRepository<TransitVehicle, Long> {

    List<TransitVehicle> findByOwnerId(Long ownerId);
}
