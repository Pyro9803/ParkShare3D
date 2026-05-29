package com.parkshare.checkin;

import com.parkshare.reservation.dto.CheckInLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CheckInLogMapper {
    CheckInLogResponse toResponse(CheckInLog log);
}
