package com.example.pettracker.event;

public record VolunteerAddedToGroupEvent(Long volunteerUserId, Long searchGroupId) {
}
