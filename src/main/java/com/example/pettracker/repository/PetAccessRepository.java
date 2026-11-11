package com.example.pettracker.repository;

import com.example.pettracker.entity.PetAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetAccessRepository extends JpaRepository<PetAccess, Long> {
    long countByPetId(Long petId);

    List<PetAccess> findByPetIdOrderByCreatedAtAsc(Long petId);

    Optional<PetAccess> findByPetIdAndUserId(Long petId, Long userId);
}
