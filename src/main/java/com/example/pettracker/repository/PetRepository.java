package com.example.pettracker.repository;

import com.example.pettracker.entity.Pet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByOwnerId(Long ownerId);

    Optional<Pet> findByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    Optional<Pet> findByImei(String imei);
}
