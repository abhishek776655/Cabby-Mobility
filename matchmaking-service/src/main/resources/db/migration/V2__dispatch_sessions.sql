-- V2: Dispatch sessions table for v2 dispatch flow

CREATE TABLE dispatch_sessions (
    dispatch_id UUID PRIMARY KEY,
    ride_id UUID NOT NULL,
    rider_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_driver_id BIGINT,
    remaining_candidates TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dispatch_status CHECK (status IN (
        'SEARCHING', 'ASSIGNMENT_SENT', 'RETRYING',
        'ASSIGNED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX idx_dispatch_ride_id ON dispatch_sessions(ride_id);
CREATE INDEX idx_dispatch_status ON dispatch_sessions(status);
CREATE INDEX idx_dispatch_expires ON dispatch_sessions(expires_at);