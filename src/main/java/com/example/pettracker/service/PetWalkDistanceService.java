package com.example.pettracker.service;

import com.example.pettracker.dto.PetWalkDistanceDayDto;
import com.example.pettracker.entity.DogWalkPosition;
import com.example.pettracker.repository.DogWalkPositionRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetWalkDistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final DogWalkPositionRepository dogWalkPositionRepository;
    private final AppTimeService appTimeService;

    public PetWalkDistanceService(
            DogWalkPositionRepository dogWalkPositionRepository,
            AppTimeService appTimeService) {
        this.dogWalkPositionRepository = dogWalkPositionRepository;
        this.appTimeService = appTimeService;
    }

    @Transactional(readOnly = true)
    public List<PetWalkDistanceDayDto> getWeeklyDistance(Long petId, LocalDate weekStart) {
        LocalDate startDate = weekStart == null
                ? LocalDate.now(appTimeService.zoneId()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : weekStart;
        LocalDate endDate = startDate.plusDays(7);
        ZoneId zoneId = appTimeService.zoneId();
        Instant from = startDate.atStartOfDay(zoneId).toInstant();
        Instant to = endDate.atStartOfDay(zoneId).toInstant();

        Map<LocalDate, Double> distanceByDate = new LinkedHashMap<>();
        for (int day = 0; day < 7; day++) {
            distanceByDate.put(startDate.plusDays(day), 0.0);
        }

        List<DogWalkPosition> positions = dogWalkPositionRepository.findByPetIdAndRecordedAtBetweenOrderByWalkAndTime(
                petId,
                from,
                to
        );

        DogWalkPosition previous = null;
        for (DogWalkPosition current : positions) {
            if (previous != null && sameWalk(previous, current)) {
                LocalDate segmentDate = LocalDate.ofInstant(current.getRecordedAt(), zoneId);
                if (distanceByDate.containsKey(segmentDate)) {
                    double segmentKm = distanceKm(
                            previous.getLatitude(),
                            previous.getLongitude(),
                            current.getLatitude(),
                            current.getLongitude()
                    );
                    distanceByDate.computeIfPresent(segmentDate, (date, totalKm) -> totalKm + segmentKm);
                }
            }
            previous = current;
        }

        List<PetWalkDistanceDayDto> response = new ArrayList<>();
        distanceByDate.forEach((date, distanceKm) -> response.add(new PetWalkDistanceDayDto(
                petId,
                date,
                date.getDayOfWeek().name(),
                round(distanceKm)
        )));
        return response;
    }

    private boolean sameWalk(DogWalkPosition first, DogWalkPosition second) {
        return first.getDogWalk() != null
                && second.getDogWalk() != null
                && first.getDogWalk().getId() != null
                && first.getDogWalk().getId().equals(second.getDogWalk().getId());
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
