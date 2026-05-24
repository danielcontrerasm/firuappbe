package com.example.pettracker.repository;

import com.example.pettracker.entity.Alert;
import com.example.pettracker.entity.Pet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByPet(Pet pet);

    List<Alert> findByPetId(Long petId);

    List<Alert> findByPetIdOrderByCreatedAtDesc(Long petId);
}
