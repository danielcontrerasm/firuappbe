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
        Pet pet = searchGroup.getPet();
        User createdBy = searchGroup.getCreatedBy();
        Set<User> volunteers = searchGroup.getVolunteers();
        List<VolunteerMemberDto> volunteerDtos = volunteers == null
                ? List.of()
                : volunteers.stream()
                        .map(SearchGroupMapper::toVolunteerMemberDto)
                        .toList();

        return new SearchGroupDto(
                searchGroup.getId(),
                searchGroup.getGroupName(),
                searchGroup.getDescription(),
                searchGroup.getStatus(),
                searchGroup.getArea(),
                searchGroup.getCity(),
                volunteerDtos.size(),
                searchGroup.getLeaderName(),
                searchGroup.getLeaderPhone(),
                searchGroup.getCoverageRadiusKm(),
                searchGroup.getCreatedAt(),
                pet == null ? null : pet.getId(),
                pet == null ? null : pet.getName(),
                pet == null || pet.getStatus() == null ? null : pet.getStatus().name(),
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getName(),
                createdBy == null ? null : createdBy.getEmail(),
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

    private static VolunteerMemberDto toVolunteerMemberDto(User user) {
        return new VolunteerMemberDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone()
        );
    }
}
