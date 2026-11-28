package com.example.pettracker.mapper;

import com.example.pettracker.dto.SearchGroupDto;
import com.example.pettracker.dto.VolunteerDto;
import com.example.pettracker.dto.VolunteerMemberDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.VolunteerRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SearchGroupMapper {

    private final VolunteerRepository volunteerRepository;

    public SearchGroupMapper(VolunteerRepository volunteerRepository) {
        this.volunteerRepository = volunteerRepository;
    }

    public SearchGroupDto toDto(SearchGroup searchGroup, User viewer) {
        return toDtos(List.of(searchGroup), viewer).getFirst();
    }

    public List<SearchGroupDto> toDtos(List<SearchGroup> groups, User viewer) {
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(SearchGroup::getId).toList();
        boolean admin = viewer.getRole() == User.Role.ADMIN;
        Map<Long, Volunteer> viewerMemberships = volunteerRepository
                .findByUserIdAndSearchGroupIdIn(viewer.getId(), groupIds).stream()
                .collect(Collectors.toMap(membership -> membership.getSearchGroup().getId(), Function.identity()));
        Map<Long, List<Volunteer>> activeMembers = admin
                ? volunteerRepository.findBySearchGroupIdInAndActiveTrueOrderByJoinedAtAsc(groupIds).stream()
                        .collect(Collectors.groupingBy(membership -> membership.getSearchGroup().getId()))
                : Map.of();
        Map<Long, Integer> activeMemberCounts = admin
                ? Map.of()
                : volunteerRepository.findActiveMemberCounts(groupIds).stream()
                        .collect(Collectors.toMap(VolunteerRepository.ActiveMemberCount::getGroupId,
                                count -> count.getMemberCount().intValue()));
        return groups.stream().map(group -> {
            List<Volunteer> members = activeMembers.getOrDefault(group.getId(), List.of());
            int memberCount = admin ? members.size() : activeMemberCounts.getOrDefault(group.getId(), 0);
            return toDto(group, viewer, admin, viewerMemberships.get(group.getId()), members, memberCount);
        }).toList();
    }

    private SearchGroupDto toDto(
            SearchGroup searchGroup,
            User viewer,
            boolean admin,
            Volunteer viewerMembership,
            List<Volunteer> activeMembers,
            int memberCount) {
        Pet pet = searchGroup.getPet();
        User createdBy = searchGroup.getCreatedBy();
        boolean creator = createdBy != null && viewer.getId().equals(createdBy.getId());
        boolean petOwner = pet != null && pet.getOwner() != null && viewer.getId().equals(pet.getOwner().getId());
        boolean revoked = viewerMembership != null && Boolean.TRUE.equals(viewerMembership.getRevoked());
        boolean active = viewerMembership != null && Boolean.TRUE.equals(viewerMembership.getActive()) && !revoked;
        List<VolunteerMemberDto> volunteerDtos = activeMembers.stream()
                .map(Volunteer::getUser)
                .filter(user -> user != null)
                .map(SearchGroupMapper::toVolunteerMemberDto)
                .toList();

        return new SearchGroupDto(
                searchGroup.getId(),
                searchGroup.getGroupName(),
                visibility(searchGroup),
                searchGroup.getDescription(),
                searchGroup.getStatus(),
                searchGroup.getArea(),
                searchGroup.getCity(),
                memberCount,
                searchGroup.getLeaderName(),
                searchGroup.getLeaderPhone(),
                searchGroup.getCoverageRadiusKm(),
                searchGroup.getCreatedAt(),
                pet == null ? null : pet.getId(),
                pet == null ? null : pet.getName(),
                pet == null || pet.getStatus() == null ? null : pet.getStatus().name(),
                imageUrl(searchGroup),
                searchGroup.getWhatsappGroupLink(),
                admin && createdBy != null ? createdBy.getId() : null,
                admin && createdBy != null ? createdBy.getName() : null,
                admin && createdBy != null ? createdBy.getEmail() : null,
                volunteerDtos,
                creator,
                admin || creator || petOwner,
                admin || petOwner,
                active,
                revoked
        );
    }

    public VolunteerDto toDto(Volunteer volunteer) {
        User user = volunteer.getUser();
        SearchGroup searchGroup = volunteer.getSearchGroup();
        Pet pet = searchGroup == null ? null : searchGroup.getPet();

        return new VolunteerDto(
                volunteer.getId(),
                user == null ? null : user.getId(),
                user == null ? null : user.getName(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(),
                searchGroup == null ? null : searchGroup.getId(),
                searchGroup == null ? null : searchGroup.getGroupName(),
                pet == null ? null : pet.getId(),
                pet == null ? null : pet.getName(),
                volunteer.getJoinedAt(),
                volunteer.getActive()
        );
    }

    private static String imageUrl(SearchGroup searchGroup) {
        return searchGroup.getImageContentType() == null || searchGroup.getId() == null
                ? null
                : "/api/search-groups/" + searchGroup.getId() + "/image";
    }

    private static String visibility(SearchGroup searchGroup) {
        return "PUBLIC".equalsIgnoreCase(searchGroup.getVisibility()) ? "PUBLIC" : "PRIVATE";
    }

    private static VolunteerMemberDto toVolunteerMemberDto(User user) {
        return new VolunteerMemberDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone()
        );
    }
}
