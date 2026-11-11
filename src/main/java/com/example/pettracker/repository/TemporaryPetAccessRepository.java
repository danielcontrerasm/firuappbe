package com.example.pettracker.repository;

import com.example.pettracker.entity.TemporaryPetAccess;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemporaryPetAccessRepository extends JpaRepository<TemporaryPetAccess, Long> {
    List<TemporaryPetAccess> findByPetIdOrderByStartsAtAsc(Long petId);

    Optional<TemporaryPetAccess> findByIdAndPetId(Long id, Long petId);

    Optional<TemporaryPetAccess> findByInvitationTokenHash(String tokenHash);

    List<TemporaryPetAccess> findByUserIdAndPetId(Long userId, Long petId);

    List<TemporaryPetAccess> findByUserId(Long userId);

    List<TemporaryPetAccess> findByExpiresAtBeforeAndActiveTrue(Instant expiresAt);
}
