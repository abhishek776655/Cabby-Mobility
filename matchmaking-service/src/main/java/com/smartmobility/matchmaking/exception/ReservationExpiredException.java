package com.smartmobility.matchmaking.exception;

public class ReservationExpiredException extends DispatchException {
    public ReservationExpiredException() {
        super("Reservation has expired", "RESERVATION_EXPIRED");
    }
}