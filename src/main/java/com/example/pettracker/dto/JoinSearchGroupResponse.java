package com.example.pettracker.dto;

/**
 * Result of attempting to join a lost-pet search group.
 *
 * The flags let clients distinguish a new membership from an idempotent
 * repeated click without having to infer it from the HTTP status alone.
 */
public record JoinSearchGroupResponse(
        VolunteerDto volunteer,
        String message,
        boolean alreadyMember,
        boolean reactivated) {
}
