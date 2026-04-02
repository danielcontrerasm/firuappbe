package com.example.pettracker.dto;


import lombok.*;
import java.util.List;

public class GeofenceRequests {
    @Data public static class CircleRequest {
        private Double centerLat;
        private Double centerLng;
        private Double radiusMeters;
    }

    @Data public static class PolygonRequest {
        // list of "lat,lng" strings or array of [lat,lng]
        private List<List<Double>> coordinates;
    }
}
