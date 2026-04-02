package com.example.pettracker.service;

import com.example.pettracker.dto.AlertResponse;
import com.example.pettracker.dto.GeofenceResponse;
import com.example.pettracker.dto.LocationResponse;
import com.example.pettracker.dto.PetSummaryResponse;
import com.example.pettracker.dto.SearchGroupResponse;
import com.example.pettracker.dto.SearchVolunteerResponse;
import com.example.pettracker.model.Alert;
import com.example.pettracker.model.Geofence;
import com.example.pettracker.model.Pet;
import com.example.pettracker.model.PetLocation;
import com.example.pettracker.model.SearchGroup;
import com.example.pettracker.model.SearchVolunteer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PetMapper {

    public PetSummaryResponse toPetSummary(Pet pet, PetLocation latestLocation, Geofence geofence) {
        return new PetSummaryResponse(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getDescription(),
                pet.isLost(),
                pet.getLostSince(),
                pet.getOwner().getId(),
                pet.getOwner().getFullName(),
                latestLocation == null ? null : toLocation(latestLocation),
                geofence == null ? null : toGeofence(geofence));
    }

    public LocationResponse toLocation(PetLocation location) {
        return new LocationResponse(
                location.getId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getSpeed(),
                location.getAccuracyMeters(),
                location.getRecordedAt());
    }

    public GeofenceResponse toGeofence(Geofence geofence) {
        return new GeofenceResponse(
                geofence.getId(),
                geofence.getCenterLatitude(),
                geofence.getCenterLongitude(),
                geofence.getRadiusMeters());
    }

    public AlertResponse toAlert(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getType().name(), alert.getMessage(), alert.getCreatedAt());
    }

    public SearchGroupResponse toSearchGroup(SearchGroup searchGroup, List<SearchVolunteer> volunteers) {
        return new SearchGroupResponse(
                searchGroup.getId(),
                searchGroup.getTitle(),
                searchGroup.getNotes(),
                searchGroup.isActive(),
                searchGroup.getCreatedAt(),
                volunteers.stream().map(this::toVolunteer).toList());
    }

    public SearchVolunteerResponse toVolunteer(SearchVolunteer volunteer) {
        return new SearchVolunteerResponse(
                volunteer.getId(),
                volunteer.getUser().getId(),
                volunteer.getUser().getFullName(),
                volunteer.getUser().getUsername(),
                volunteer.getJoinedAt());
    }
}
