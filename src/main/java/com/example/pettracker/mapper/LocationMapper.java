package com.example.pettracker.mapper;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.dto.PetNeighborhoodDto;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationMapper INSTANCE = Mappers.getMapper(LocationMapper.class);

    default LocationDTO toDto(Location location) {
        return toDto(location, null);
    }

    default LocationDTO toDto(Location location, PetNeighborhoodDto neighborhood) {
        if (location == null) {
            return null;
        }

        Pet pet = location.getPet();
        return new LocationDTO(
                location.getId(),
                pet == null ? null : pet.getId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getBatteryPercent(),
                location.getBatteryVoltage(),
                pet == null ? null : pet.getName(),
                location.getTimestamp() == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(location.getTimestamp()),
                neighborhood == null ? location.getCity() : neighborhood.city(),
                neighborhood == null ? location.getAddress() : neighborhood.displayName(),
                neighborhood == null ? location.getNeighborhood() : neighborhood.neighborhood(),
                neighborhood == null ? location.getComuna() : neighborhood.district(),
                neighborhood == null ? location.getNeighborhoodResolved() : neighborhood.resolved(),
                neighborhood == null ? location.getNeighborhoodSource() : neighborhood.source()
        );
    }
}
