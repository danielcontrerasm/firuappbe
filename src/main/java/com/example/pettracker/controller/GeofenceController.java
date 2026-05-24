package com.example.pettracker.controller;

import com.example.pettracker.dto.GeofenceRequests.*;
import com.example.pettracker.dto.GeofenceResponseDto;
import com.example.pettracker.entity.Geofence;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.mapper.GeofenceMapper;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.GeofenceRepository;
import org.locationtech.jts.geom.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {
    private final GeofenceRepository geofenceRepository;
    private final PetRepository petRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public GeofenceController(GeofenceRepository geofenceRepository, PetRepository petRepository) {
        this.geofenceRepository = geofenceRepository;
        this.petRepository = petRepository;
    }

    @PostMapping("/circle/{petId}")
    public ResponseEntity<GeofenceResponseDto> createCircle(@PathVariable Long petId, @RequestBody CircleRequest req) {
        Pet pet = petRepository.findById(petId).orElseThrow();
        Geofence g = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.CIRCLE)
                .centerLat(req.getCenterLat())
                .centerLng(req.getCenterLng())
                .radiusMeters(req.getRadiusMeters())
                .build();
        return ResponseEntity.ok(GeofenceMapper.toDto(geofenceRepository.save(g)));
    }

    @PostMapping("/polygon/{petId}")
    public ResponseEntity<GeofenceResponseDto> createPolygon(@PathVariable Long petId, @RequestBody PolygonRequest req) {
        Pet pet = petRepository.findById(petId).orElseThrow();
        // coordinates are list of "lat,lng" strings
        List<List<Double>> coords = req.getCoordinates();
        Coordinate[] c = new Coordinate[coords.size() + 1];

        for (int i = 0; i < coords.size(); i++) {
            double lat = coords.get(i).get(0);
            double lng = coords.get(i).get(1);
            c[i] = new Coordinate(lng, lat); // note order: lng, lat for geometry
        }
        c[coords.size()] = c[0]; // close polygon

        Polygon poly = geometryFactory.createPolygon(c);
        Geofence g = Geofence.builder()
                .pet(pet)
                .type(Geofence.Type.POLYGON)
                .polygon(poly)
                .build();


        return ResponseEntity.ok(GeofenceMapper.toDto(geofenceRepository.save(g)));
    }

    @GetMapping("/{petId}")
    public ResponseEntity<GeofenceResponseDto> getByPet(@PathVariable Long petId) {
        return geofenceRepository.findByPetId(petId)
                .map(GeofenceMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{petId}")
    public ResponseEntity<Void> delete(@PathVariable Long petId) {
        var opt = geofenceRepository.findByPetId(petId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        geofenceRepository.delete(opt.get());
        return ResponseEntity.noContent().build();
    }
}
