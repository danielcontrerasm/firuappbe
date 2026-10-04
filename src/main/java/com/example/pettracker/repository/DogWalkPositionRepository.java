package com.example.pettracker.repository;

import com.example.pettracker.entity.DogWalkPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DogWalkPositionRepository extends JpaRepository<DogWalkPosition, Long> {

    List<DogWalkPosition> findByDogWalkIdOrderByRecordedAtAsc(Long dogWalkId);

    List<DogWalkPosition> findByDogWalkIdOrderByRecordedAtAsc(Long dogWalkId, Pageable pageable);

    Optional<DogWalkPosition> findTopByDogWalkIdOrderByRecordedAtDesc(Long dogWalkId);

    @Query("""
            SELECT position
            FROM DogWalkPosition position
            WHERE position.dogWalk.id IN :dogWalkIds
              AND position.recordedAt = (
                  SELECT MAX(latest.recordedAt)
                  FROM DogWalkPosition latest
                  WHERE latest.dogWalk.id = position.dogWalk.id
              )
            """)
    List<DogWalkPosition> findLatestByDogWalkIds(@Param("dogWalkIds") List<Long> dogWalkIds);
}
