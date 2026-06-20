package com.example.pettracker.repository;

import com.example.pettracker.entity.WalkMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkMessageRepository extends JpaRepository<WalkMessage, Long> {

    List<WalkMessage> findByWalkRequestIdOrderByCreatedAtAsc(Long walkRequestId);
}
