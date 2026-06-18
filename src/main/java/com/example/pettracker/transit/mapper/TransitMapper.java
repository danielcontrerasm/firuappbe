package com.example.pettracker.transit.mapper;

import com.example.pettracker.entity.User;
import com.example.pettracker.transit.dto.TransitLocationDto;
import com.example.pettracker.transit.dto.TransitScheduledGeofenceDto;
import com.example.pettracker.transit.dto.TransitSearchGroupDto;
import com.example.pettracker.transit.dto.TransitVehicleDto;
import com.example.pettracker.transit.entity.TransitLocation;
import com.example.pettracker.transit.entity.TransitScheduledGeofence;
import com.example.pettracker.transit.entity.TransitSearchGroup;
import com.example.pettracker.transit.entity.TransitVehicle;
import java.util.List;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

public final class TransitMapper {

    private TransitMapper() {
    }

    public static TransitVehicleDto toDto(TransitVehicle vehicle) {
        User owner = vehicle.getOwner();
        return new TransitVehicleDto(
                vehicle.getId(),
                vehicle.getLabel(),
                vehicle.getPlateNumber(),
                vehicle.getRouteCode(),
                vehicle.getOperatorName(),
                vehicle.getType() == null ? null : vehicle.getType().name(),
                vehicle.getStatus() == null ? null : vehicle.getStatus().name(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getName(),
                vehicle.getCreatedAt());
    }

    public static TransitLocationDto toDto(TransitLocation location) {
        TransitVehicle vehicle = location.getVehicle();
        return new TransitLocationDto(
                location.getId(),
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getLabel(),
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp(),
                location.getSource());
    }

    public static TransitScheduledGeofenceDto toDto(TransitScheduledGeofence geofence) {
        return new TransitScheduledGeofenceDto(
                geofence.getId(),
                geofence.getVehicle() == null ? null : geofence.getVehicle().getId(),
                geofence.getName(),
                geofence.getZoneType() == null ? null : geofence.getZoneType().name(),
                geofence.getShapeType() == null ? null : geofence.getShapeType().name(),
                geofence.getCenterLat(),
                geofence.getCenterLng(),
                geofence.getRadiusMeters(),
                polygonToCoordinates(geofence.getPolygon()),
                geofence.getActiveFrom() == null ? null : geofence.getActiveFrom().toString(),
                geofence.getActiveTo() == null ? null : geofence.getActiveTo().toString(),
                geofence.getActiveDays(),
                geofence.getEnabled());
    }

    public static TransitSearchGroupDto toDto(TransitSearchGroup group) {
        Set<User> volunteers = group.getVolunteers();
        List<Long> volunteerUserIds = volunteers == null
                ? List.of()
                : volunteers.stream().map(User::getId).toList();

        return new TransitSearchGroupDto(
                group.getId(),
                group.getVehicle() == null ? null : group.getVehicle().getId(),
                group.getVehicle() == null ? null : group.getVehicle().getLabel(),
                group.getGroupName(),
                group.getDescription(),
                group.getStatus(),
                group.getArea(),
                group.getCity(),
                group.getCoverageRadiusKm(),
                group.getLeaderName(),
                group.getLeaderPhone(),
                group.getIncidentType(),
                group.getCreatedAt(),
                group.getCreatedBy() == null ? null : group.getCreatedBy().getId(),
                group.getCreatedBy() == null ? null : group.getCreatedBy().getName(),
                volunteerUserIds.size(),
                volunteerUserIds);
    }

    private static List<List<Double>> polygonToCoordinates(Polygon polygon) {
        if (polygon == null) {
            return List.of();
        }
        return List.of(polygon.getCoordinates()).stream()
                .map(TransitMapper::toLatLng)
                .toList();
    }

    private static List<Double> toLatLng(Coordinate coordinate) {
        return List.of(coordinate.y, coordinate.x);
    }
}
