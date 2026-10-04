package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogWalkRepository extends JpaRepository<DogWalk, Long> {

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    Optional<DogWalk> findByWalkRequestId(Long walkRequestId);

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<DogWalk> findByWalkRequestIdIn(List<Long> walkRequestIds);

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<DogWalk> findByOwnerIdOrderByStartedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<DogWalk> findByOwnerIdOrderByStartedAtDesc(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<DogWalk> findByWalkerProfileUserIdOrderByStartedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    List<DogWalk> findByWalkerProfileUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"walkRequest", "owner", "walkerProfile", "walkerProfile.user", "pet"})
    Optional<DogWalk> findById(Long id);
}
