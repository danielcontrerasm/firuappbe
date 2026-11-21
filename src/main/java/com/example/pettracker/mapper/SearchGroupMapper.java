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
import org.springframework.stereotype.Component;

@Component
public class SearchGroupMapper {

    private final VolunteerRepository volunteerRepository;

    public SearchGroupMapper(VolunteerRepository volunteerRepository) {
        this.volunteerRepository = volunteerRepository;
    }

    public SearchGroupDto toDto(SearchGroup searchGroup) {
        return toDto(searchGroup, true, false);
    }

    /** Public lost-wall representation without account or volunteer user details. */
    public SearchGroupDto toPublicDto(SearchGroup searchGroup) {
        return toDto(searchGroup, false, false);
    }

    public SearchGroupDto toPublicDto(SearchGroup searchGroup, boolean canClose) {
        return toDto(searchGroup, false, canClose);
    }

    public SearchGroupDto toDto(SearchGroup searchGroup, boolean includePrivateDetails, boolean canClose) {
        return toDtoInternal(searchGroup, includePrivateDetails, canClose);
    }

    public SearchGroupDto toDto(SearchGroup searchGroup, boolean includePrivateDetails) {
        return toDtoInternal(searchGroup, includePrivateDetails, false);
    }

    private SearchGroupDto toDtoInternal(SearchGroup searchGroup, boolean includePrivateDetails, boolean canClose) {
        Pet pet = searchGroup.getPet();
        User createdBy = searchGroup.getCreatedBy();
        List<Volunteer> memberships = volunteerRepository
                .findBySearchGroupIdAndActiveTrueOrderByJoinedAtAsc(searchGroup.getId());
        List<VolunteerMemberDto> volunteerDtos = !includePrivateDetails
                ? List.of()
                : memberships.stream()
                        .map(Volunteer::getUser)
                        .filter(user -> user != null)
                        .map(SearchGroupMapper::toVolunteerMemberDto)
                        .toList();
        int volunteerCount = memberships.size();

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
                volunteerDtos,
                canClose
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
