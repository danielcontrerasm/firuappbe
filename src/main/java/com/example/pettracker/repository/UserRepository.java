package com.example.pettracker.repository;

import com.example.pettracker.dto.VolunteerCandidateDto;
import com.example.pettracker.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT user FROM User user WHERE LOWER(user.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("""
            SELECT new com.example.pettracker.dto.VolunteerCandidateDto(user.id, user.name, user.email)
            FROM User user
            WHERE (
                LOCATE(LOWER(:query), LOWER(COALESCE(user.name, ''))) > 0
                OR LOCATE(LOWER(:query), LOWER(user.email)) > 0
            )
            AND NOT EXISTS (
                SELECT volunteer.id
                FROM Volunteer volunteer
                WHERE volunteer.user.id = user.id
                    AND volunteer.searchGroup.id = :searchGroupId
                    AND volunteer.active = true
            )
            """)
    List<VolunteerCandidateDto> searchVolunteerCandidates(
            @Param("searchGroupId") Long searchGroupId,
            @Param("query") String query,
            Pageable pageable);
}
