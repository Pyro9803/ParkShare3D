package com.parkshare.shared.exception;

public class CheckInWindowException extends BusinessException {

    public CheckInWindowException(String message) {
        super("CHECKIN_WINDOW_EXPIRED", message);
    }
}
