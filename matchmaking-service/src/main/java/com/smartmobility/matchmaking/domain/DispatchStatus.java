package com.smartmobility.matchmaking.domain;

public enum DispatchStatus {
    SEARCHING,
    // No candidates found (or all exhausted) at the current search radius;
    // waiting for the scheduler to retry discovery at the next wider radius.
    WIDENING_SEARCH,
    ASSIGNMENT_SENT,
    RETRYING,
    ASSIGNED,
    FAILED,
    CANCELLED
}