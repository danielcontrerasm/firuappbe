package com.example.pettracker.repository;

import com.example.pettracker.entity.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {

    Volunteer findByUserIdAndSearchGroupId(Long userId, Long searchGroupId);

    List<Volunteer> findBySearchGroupIdAndActiveTrue(Long searchGroupId);

    List<Volunteer> findByUserIdAndActiveTrue(Long userId);

    List<Volunteer> findBySearchGroupId(Long searchGroupId);

    List<Volunteer> findByActiveTrueAndSearchGroupId(Long searchGroupId);

    long countBySearchGroupIdAndActiveTrue(Long searchGroupId);

    List<Volunteer> findBySearchGroup_PetId(Long petId);
}
