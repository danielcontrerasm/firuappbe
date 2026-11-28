package com.example.pettracker.repository;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SearchGroupRepository extends JpaRepository<SearchGroup, Long> {

    // REVIEWED
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SearchGroup g WHERE g.id = :id")
    java.util.Optional<SearchGroup> findByIdForUpdate(@Param("id") Long id);

    List<SearchGroup> findByPet(Pet pet);

    List<SearchGroup> findByPetId(Long petId);

    List<SearchGroup> findByPetId(Long petId, Pageable pageable);

    boolean existsByPetId(Long petId);

    long countByCreatedById(Long userId);

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

    @Query("""
            SELECT DISTINCT g
            FROM SearchGroup g
            LEFT JOIN Volunteer v ON v.searchGroup = g
                AND v.user.id = :userId
                AND v.active = true
            WHERE (UPPER(COALESCE(g.status, 'ACTIVE')) = 'ACTIVE'
                   AND (UPPER(COALESCE(g.visibility, 'PRIVATE')) = 'PUBLIC'
                        OR v.id IS NOT NULL))
               OR (UPPER(COALESCE(g.status, 'ACTIVE')) = 'CLOSED'
                   AND v.id IS NOT NULL)
            """)
    List<SearchGroup> findDiscoverableForUser(@Param("userId") Long userId, Pageable pageable);

}
