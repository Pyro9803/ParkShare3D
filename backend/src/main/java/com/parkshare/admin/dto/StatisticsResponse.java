package com.parkshare.admin.dto;

import java.math.BigDecimal;

public record StatisticsResponse(
        long totalUsers,
        long totalReservations,
        BigDecimal totalRevenue,
        long activeSpots
) {
}
