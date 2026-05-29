package com.parkshare.reservation;

public enum ReservationStatus {
    RESERVED,
    CANCELLED,
    CHECKED_IN,
    COMPLETED,
    EXPIRED,  // set by scheduler when RESERVED past startTime+45min without check-in
    NO_SHOW   // set by scheduler when CHECKED_IN past endTime+1h without check-out
}
