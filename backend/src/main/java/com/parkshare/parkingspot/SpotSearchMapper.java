package com.parkshare.parkingspot;

import java.math.BigDecimal;

import com.parkshare.parkingspot.dto.SpotSearchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SpotSearchMapper {

    @Mapping(source = "estimatedPrice", target = "estimatedPrice")
    @Mapping(source = "spot.id", target = "id")
    @Mapping(source = "spot.lotId", target = "lotId")
    @Mapping(source = "spot.code", target = "code")
    @Mapping(source = "spot.x", target = "x")
    @Mapping(source = "spot.y", target = "y")
    @Mapping(source = "spot.z", target = "z")
    @Mapping(source = "spot.width", target = "width")
    @Mapping(source = "spot.length", target = "length")
    @Mapping(source = "spot.vehicleType", target = "vehicleType")
    @Mapping(source = "spot.pricePerHour", target = "pricePerHour")
    SpotSearchResponse toResponse(ParkingSpot spot, BigDecimal estimatedPrice);
}
