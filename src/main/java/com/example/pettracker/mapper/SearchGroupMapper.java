package com.example.pettracker.mapper;

import com.example.pettracker.dto.SearchGroupDto;
import com.example.pettracker.dto.VolunteerDto;
import com.example.pettracker.dto.VolunteerMemberDto;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import java.util.List;
import java.util.Set;

public class SearchGroupMapper {

    private SearchGroupMapper() {
    }

    public static SearchGroupDto toDto(SearchGroup searchGroup) {
        return toDto(searchGroup, true);
    }

    /** Public lost-wall representation without account or volunteer user details. */
    public static SearchGroupDto toPublicDto(SearchGroup searchGroup) {
        return toDto(searchGroup, false);
    }

    public static SearchGroupDto toDto(SearchGroup searchGroup, boolean includePrivateDetails) {
        Pet pet = searchGroup.getPet();
        User createdBy = searchGroup.getCreatedBy();
        Set<User> volunteers = searchGroup.getVolunteers();
        List<VolunteerMemberDto> volunteerDtos = !includePrivateDetails || volunteers == null
                ? List.of()
                : volunteers.stream()
                        .map(SearchGroupMapper::toVolunteerMemberDto)
                        .toList();
        int volunteerCount = volunteers == null ? 0 : volunteers.size();

        return new SearchGroupDto(
                searchGroup.getId(),
                searchGroup.getGroupName(),
                visibility(searchGroup),
                searchGroup.getDescription(),
                searchGroup.getStatus(),
                searchGroup.getArea(),
                searchGroup.getCity(),
                volunteerCount,
                searchGroup.getLeaderName(),
                searchGroup.getLeaderPhone(),
                searchGroup.getCoverageRadiusKm(),
                searchGroup.getCreatedAt(),
                pet == null ? null : pet.getId(),
                pet == null ? null : pet.getName(),
                pet == null || pet.getStatus() == null ? null : pet.getStatus().name(),
                imageUrl(searchGroup),
                searchGroup.getWhatsappGroupLink(),
                includePrivateDetails && createdBy != null ? createdBy.getId() : null,
                includePrivateDetails && createdBy != null ? createdBy.getName() : null,
                includePrivateDetails && createdBy != null ? createdBy.getEmail() : null,
                volunteerDtos
        );
    }

    public static VolunteerDto toDto(Volunteer volunteer) {
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
