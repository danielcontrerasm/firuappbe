package com.example.pettracker.repository;

import com.example.pettracker.model.PetLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetLocationRepository extends JpaRepository<PetLocation, Long> {

    List<PetLocation> findTop20ByPetIdOrderByRecordedAtDesc(Long petId);

    List<PetLocation> findByPetIdOrderByRecordedAtDesc(Long petId);
}
