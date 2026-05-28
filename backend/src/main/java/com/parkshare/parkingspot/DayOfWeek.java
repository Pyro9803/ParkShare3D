package com.parkshare.parkingspot;

public enum DayOfWeek {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    public static DayOfWeek from(java.time.DayOfWeek javaDay) {
        return valueOf(javaDay.name().substring(0, 3));
    }
}
