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

    @Query("""
            SELECT DISTINCT g
            FROM SearchGroup g
            JOIN Volunteer v ON v.searchGroup = g
            WHERE v.user.id = :userId
              AND v.active = true
            """)
    List<SearchGroup> findByActiveVolunteerUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT g
            FROM SearchGroup g
            JOIN Volunteer v ON v.searchGroup = g
            WHERE g.pet.id = :petId
              AND v.user.id = :userId
              AND v.active = true
            """)
    List<SearchGroup> findByPetIdAndActiveVolunteerUserId(
            @Param("petId") Long petId,
            @Param("userId") Long userId,
            Pageable pageable);

}
