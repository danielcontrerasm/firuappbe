package com.example.pettracker.repository;

import com.example.pettracker.entity.Geofence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceRepository extends JpaRepository<Geofence, Long> {

    Optional<Geofence> findByPetId(Long petId);
}
