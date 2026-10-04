package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalkerProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogWalkerProfileRepository extends JpaRepository<DogWalkerProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<DogWalkerProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<DogWalkerProfile> findByApprovalStatusAndActiveTrueOrderByCreatedAtDesc(DogWalkerProfile.ApprovalStatus approvalStatus);

    @EntityGraph(attributePaths = "user")
    List<DogWalkerProfile> findByApprovalStatusAndActiveTrueOrderByCreatedAtDesc(
            DogWalkerProfile.ApprovalStatus approvalStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<DogWalkerProfile> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    List<DogWalkerProfile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<DogWalkerProfile> findById(Long id);
}
