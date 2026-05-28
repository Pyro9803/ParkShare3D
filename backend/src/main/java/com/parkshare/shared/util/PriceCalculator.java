package com.parkshare.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PriceCalculator {

    private static final BigDecimal ROUNDING_UNIT = BigDecimal.valueOf(1000);

    /**
     * Calculates the total price based on duration and hourly rate,
     * rounded to the nearest 1000 VND.
     */
    public static BigDecimal calculate(LocalDateTime start, LocalDateTime end, BigDecimal pricePerHour) {
        BigDecimal durationHours = BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        BigDecimal rawPrice = pricePerHour.multiply(durationHours);

        return roundToNearest1000(rawPrice);
    }

    public static BigDecimal roundToNearest1000(BigDecimal amount) {
        return amount.divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
                .multiply(ROUNDING_UNIT)
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
