package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogWalkRepository extends JpaRepository<DogWalk, Long> {

    Optional<DogWalk> findByWalkRequestId(Long walkRequestId);

    List<DogWalk> findByOwnerIdOrderByStartedAtDesc(Long ownerId);

    List<DogWalk> findByWalkerProfileUserIdOrderByStartedAtDesc(Long userId);
}
