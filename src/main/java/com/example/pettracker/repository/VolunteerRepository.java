package com.example.pettracker.repository;

import com.example.pettracker.entity.Volunteer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {

    Volunteer findByUserIdAndSearchGroupId(Long userId, Long searchGroupId);

    List<Volunteer> findBySearchGroupIdAndActiveTrue(Long searchGroupId);

    List<Volunteer> findBySearchGroupIdAndActiveTrueOrderByJoinedAtAsc(Long searchGroupId);

    List<Volunteer> findBySearchGroupIdAndActiveTrue(Long searchGroupId, Pageable pageable);

    List<Volunteer> findByUserIdAndActiveTrue(Long userId);

    List<Volunteer> findByUserIdAndActiveTrue(Long userId, Pageable pageable);

    List<Volunteer> findBySearchGroupId(Long searchGroupId);

    List<Volunteer> findByActiveTrueAndSearchGroupId(Long searchGroupId);

    long countBySearchGroupIdAndActiveTrue(Long searchGroupId);

    List<Volunteer> findBySearchGroup_PetIdAndActiveTrue(Long petId);

    @Query("""
            SELECT v
            FROM Volunteer v
            JOIN FETCH v.user
            JOIN FETCH v.searchGroup g
            WHERE g.pet.id = :petId
              AND v.active = true
              AND UPPER(COALESCE(g.status, 'ACTIVE')) = 'ACTIVE'
            """)
    List<Volunteer> findActiveMembersOfActiveGroupsByPetId(@Param("petId") Long petId);
}
