package com.example.pettracker.repository;

import com.example.pettracker.dto.SearchGroupRejoinOptionDto;
import com.example.pettracker.entity.VolunteerRejoinRequest;
import com.example.pettracker.entity.VolunteerRejoinRequest.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VolunteerRejoinRequestRepository extends JpaRepository<VolunteerRejoinRequest, Long> {

    @Query("""
            SELECT new com.example.pettracker.dto.SearchGroupRejoinOptionDto(g.id, g.groupName, MAX(r.id))
            FROM Volunteer v
            JOIN v.searchGroup g
            LEFT JOIN VolunteerRejoinRequest r ON r.volunteer = v AND r.status = :pendingStatus
            WHERE v.user.id = :userId
              AND v.revoked = true
              AND COALESCE(v.active, false) = false
              AND UPPER(COALESCE(g.status, 'ACTIVE')) = 'ACTIVE'
            GROUP BY v.id, g.id, g.groupName
            ORDER BY v.id DESC
            """)
    List<SearchGroupRejoinOptionDto> findRejoinOptions(
            @Param("userId") Long userId,
            @Param("pendingStatus") Status pendingStatus,
            Pageable pageable);

    // REVIEWED
    @EntityGraph(attributePaths = {"volunteer.user", "searchGroup.createdBy"})
    Optional<VolunteerRejoinRequest> findFirstByVolunteerIdAndStatusOrderByCreatedAtDesc(
            Long volunteerId,
            Status status);

    // REVIEWED
    @EntityGraph(attributePaths = {"volunteer.user", "searchGroup.createdBy"})
    Optional<VolunteerRejoinRequest> findByIdAndSearchGroupId(Long id, Long searchGroupId);

    // REVIEWED
    @Query("""
            SELECT r
            FROM VolunteerRejoinRequest r
            JOIN FETCH r.volunteer v
            JOIN FETCH v.user
            JOIN FETCH r.searchGroup g
            LEFT JOIN FETCH g.createdBy
            WHERE r.id = :id
            """)
    Optional<VolunteerRejoinRequest> findWithDetailsById(@Param("id") Long id);

    // REVIEWED
    @EntityGraph(attributePaths = {"volunteer.user", "searchGroup"})
    List<VolunteerRejoinRequest> findBySearchGroupIdAndStatusOrderByCreatedAtAsc(
            Long searchGroupId,
            Status status);
}
