package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalkerProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogWalkerProfileRepository extends JpaRepository<DogWalkerProfile, Long> {

    Optional<DogWalkerProfile> findByUserId(Long userId);

    List<DogWalkerProfile> findByApprovalStatusAndActiveTrueOrderByCreatedAtDesc(DogWalkerProfile.ApprovalStatus approvalStatus);

    List<DogWalkerProfile> findAllByOrderByCreatedAtDesc();
}
