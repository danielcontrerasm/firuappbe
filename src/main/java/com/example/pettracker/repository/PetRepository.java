package com.example.pettracker.repository;

import com.example.pettracker.entity.Pet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    List<Pet> findAll();

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Pet> findById(Long id);

    @EntityGraph(attributePaths = "owner")
    List<Pet> findByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    Optional<Pet> findByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    @EntityGraph(attributePaths = "owner")
    Optional<Pet> findByImei(String imei);


    @EntityGraph(attributePaths = "owner")
    Optional<Pet> findByTerminalId(String imei);
}
