package com.example.pettracker.repository;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchGroupRepository extends JpaRepository<SearchGroup, Long> {

    List<SearchGroup> findByPet(Pet pet);

    List<SearchGroup> findByPetId(Long petId);

    List<SearchGroup> findByPetId(Long petId, Pageable pageable);

    List<SearchGroup> findByPetStatus(Pet.Status status, Pageable pageable);

    @Query("""
            SELECT g
            FROM SearchGroup g
            WHERE g.pet.owner.id = :ownerId
               OR g.pet.status = :status
            """)
    List<SearchGroup> findVisibleToUser(
            @Param("ownerId") Long ownerId,
            @Param("status") Pet.Status status,
            Pageable pageable);
}
