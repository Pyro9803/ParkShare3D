package com.parkshare.reservation;

import com.parkshare.reservation.dto.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReservationMapper {
    ReservationResponse toResponse(Reservation reservation);
}
