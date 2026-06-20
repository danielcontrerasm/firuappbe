package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalkPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogWalkPositionRepository extends JpaRepository<DogWalkPosition, Long> {

    List<DogWalkPosition> findByDogWalkIdOrderByRecordedAtAsc(Long dogWalkId);

    Optional<DogWalkPosition> findTopByDogWalkIdOrderByRecordedAtDesc(Long dogWalkId);
}
