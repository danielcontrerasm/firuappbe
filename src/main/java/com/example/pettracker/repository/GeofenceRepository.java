package com.example.pettracker.repository;

import com.example.pettracker.entity.Geofence;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceRepository extends JpaRepository<Geofence, Long> {

    Optional<Geofence> findByPetId(Long petId);

    List<Geofence> findAllByPetIdOrderByIdAsc(Long petId);

    long countByPetId(Long petId);

    void deleteByPetId(Long petId);
}
