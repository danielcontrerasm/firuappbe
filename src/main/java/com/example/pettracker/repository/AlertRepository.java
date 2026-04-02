package com.example.pettracker.repository;

import com.example.pettracker.model.Alert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByPetIdOrderByCreatedAtDesc(Long petId);
}
