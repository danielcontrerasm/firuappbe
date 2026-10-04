package com.example.pettracker.repository;

import com.example.pettracker.entity.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    @EntityGraph(attributePaths = "pet")
    List<Location> findByPetIdOrderByTimestampDesc(Long petId, Pageable pageable);

    @EntityGraph(attributePaths = "pet")
    Optional<Location> findTopByPetIdOrderByTimestampDesc(Long petId);

    @Query("SELECT l FROM Location l WHERE l.pet.owner.id = :userId")
    List<Location> findByUserId(@Param("userId") Long userId);

    @Query(value = """
    SELECT DISTINCT ON (l.pet_id) l.*
    FROM location l
    JOIN pet p ON p.id = l.pet_id
    WHERE p.owner_id = :userId
    ORDER BY l.pet_id, l.timestamp DESC
    """, nativeQuery = true)
    List<Location> findLastLocationsByUserId(@Param("userId") Long userId);

    @Query("SELECT l FROM Location l WHERE l.pet.id = :petId AND l.timestamp >= :cutoff ORDER BY l.timestamp ASC")
    List<Location> findPetRouteSince(@Param("petId") Long petId, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);


    @Override
    @EntityGraph(attributePaths = "pet")
    List<Location> findAll();

    @Query("""
    SELECT l
    FROM Location l
    WHERE l.timestamp = (
        SELECT MAX(l2.timestamp)
        FROM Location l2
        WHERE l2.pet.id = l.pet.id
    )
""")
    List<Location> findLastLocationsForAllPets();
}
