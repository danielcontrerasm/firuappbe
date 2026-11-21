package com.example.pettracker.event;

public record SearchGroupCreatedEvent(
        Long searchGroupId,
        String groupName,
        String petName,
        String petType,
        String area,
        String city) {
}
