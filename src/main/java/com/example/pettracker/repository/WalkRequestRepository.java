package com.example.pettracker.repository;

import com.example.pettracker.entity.WalkRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkRequestRepository extends JpaRepository<WalkRequest, Long> {

    List<WalkRequest> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<WalkRequest> findByWalkerProfileUserIdOrderByCreatedAtDesc(Long userId);
}
