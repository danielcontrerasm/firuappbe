package com.example.pettracker.mapper;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.entity.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationMapper INSTANCE = Mappers.getMapper(LocationMapper.class);

    @Mapping(source = "pet.name", target = "petName")
    @Mapping(source = "pet.id", target = "petId")
    LocationDTO toDto(Location location);
}
