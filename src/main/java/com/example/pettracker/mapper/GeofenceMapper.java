package com.example.pettracker.mapper;

import com.example.pettracker.dto.GeofenceResponseDto;
import com.example.pettracker.entity.Geofence;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

public class GeofenceMapper {

    private GeofenceMapper() {
    }

    public static GeofenceResponseDto toDto(Geofence geofence) {
        return new GeofenceResponseDto(
                geofence.getId(),
                geofence.getPet().getId(),
                geofence.getPet().getName(),
                geofence.getType().name(),
                geofence.getCenterLat(),
                geofence.getCenterLng(),
                geofence.getRadiusMeters(),
                polygonToCoordinates(geofence.getPolygon())
        );
    }

    private static List<List<Double>> polygonToCoordinates(Polygon polygon) {
        if (polygon == null) {
            return null;
        }

        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
        return Arrays.stream(coordinates)
                .map(coord -> List.of(coord.x, coord.y))
                .toList();
    }
}
