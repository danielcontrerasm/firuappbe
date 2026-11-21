package com.example.pettracker.event;

public record VolunteerJoinedGroupEvent(Long volunteerUserId, Long searchGroupId) {
}
