package com.parkshare.reservation;

public enum ReservationStatus {
    RESERVED,
    CANCELLED,
    CHECKED_IN,
    COMPLETED,
    NO_SHOW  // set by scheduled job in Task 1.10 when driver doesn't check in within the window
}
