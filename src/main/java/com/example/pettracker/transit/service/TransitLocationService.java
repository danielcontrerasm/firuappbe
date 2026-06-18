package com.example.pettracker.transit.service;

import com.example.pettracker.transit.dto.TransitLocationDto;
import com.example.pettracker.transit.dto.TransitLocationRequest;
import com.example.pettracker.transit.entity.TransitLocation;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.mapper.TransitMapper;
import com.example.pettracker.transit.repository.TransitLocationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransitLocationService {

    private final TransitLocationRepository transitLocationRepository;
    private final TransitLocationIngestionService transitLocationIngestionService;

    public TransitLocationService(
            TransitLocationRepository transitLocationRepository,
            TransitLocationIngestionService transitLocationIngestionService) {
        this.transitLocationRepository = transitLocationRepository;
        this.transitLocationIngestionService = transitLocationIngestionService;
    }

    public TransitLocationDto save(TransitVehicle vehicle, TransitLocationRequest request) {
        TransitLocation location = TransitLocation.builder()
                .vehicle(vehicle)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .timestamp(request.timestamp() == null ? LocalDateTime.now() : request.timestamp())
                .source(request.source())
                .build();
        return TransitMapper.toDto(transitLocationIngestionService.ingest(location));
    }

    public TransitLocation getLatestByVehicleId(Long vehicleId) {
        return transitLocationRepository.findTopByVehicleIdOrderByTimestampDesc(vehicleId).orElse(null);
    }

    public TransitLocationDto getLatestDtoByVehicleId(Long vehicleId) {
        TransitLocation latestLocation = getLatestByVehicleId(vehicleId);
        return latestLocation == null ? null : TransitMapper.toDto(latestLocation);
    }

    public List<TransitLocationDto> getRouteForLastHours(Long vehicleId, int hours) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return transitLocationRepository.findByVehicleIdAndTimestampGreaterThanEqualOrderByTimestampAsc(vehicleId, from)
                .stream()
                .map(TransitMapper::toDto)
                .toList();
    }
}
