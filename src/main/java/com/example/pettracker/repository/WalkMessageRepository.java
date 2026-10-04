package com.example.pettracker.repository;

import com.example.pettracker.entity.WalkMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkMessageRepository extends JpaRepository<WalkMessage, Long> {

    @EntityGraph(attributePaths = {"walkRequest", "sender"})
    List<WalkMessage> findByWalkRequestIdOrderByCreatedAtAsc(Long walkRequestId);

    @EntityGraph(attributePaths = {"walkRequest", "sender"})
    List<WalkMessage> findByWalkRequestIdOrderByCreatedAtAsc(Long walkRequestId, Pageable pageable);
}
