package com.example.pettracker.transit.repository;

import com.example.pettracker.transit.entity.TransitVolunteer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitVolunteerRepository extends JpaRepository<TransitVolunteer, Long> {

    TransitVolunteer findByUserIdAndSearchGroupId(Long userId, Long searchGroupId);

    List<TransitVolunteer> findBySearchGroupIdAndActiveTrue(Long searchGroupId);

    List<TransitVolunteer> findByUserIdAndActiveTrue(Long userId);
}
