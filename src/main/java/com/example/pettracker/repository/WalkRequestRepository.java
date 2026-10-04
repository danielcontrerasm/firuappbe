package com.example.pettracker.repository;

import com.example.pettracker.entity.WalkRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface WalkRequestRepository extends JpaRepository<WalkRequest, Long> {

    @EntityGraph(attributePaths = {"owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<WalkRequest> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<WalkRequest> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<WalkRequest> findByWalkerProfileUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<WalkRequest> findByWalkerProfileUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"owner", "walkerProfile", "walkerProfile.user", "pet"})
    java.util.Optional<WalkRequest> findById(Long id);
}
