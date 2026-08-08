package com.smartmobility.matchmaking.scheduler;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import com.smartmobility.matchmaking.service.DispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchTimeoutSchedulerTest {

    @Mock
    private DispatchSessionRepository dispatchRepository;

    @Mock
    private DispatchService dispatchService;

    private DispatchTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DispatchTimeoutScheduler(dispatchRepository, dispatchService);
    }

    @Test
    void handleExpiredAssignments_DelegatesAssignmentSentSessionsToDispatchService() {
        DispatchSessionEntity session = buildSession(DispatchStatus.ASSIGNMENT_SENT);
        when(dispatchRepository.findExpiredDispatchSessions(any())).thenReturn(List.of(session));

        scheduler.handleExpiredAssignments();

        verify(dispatchService).handleDispatchTimeout(session.getDispatchId());
    }

    @Test
    void handleExpiredAssignments_DelegatesRetryingSessionsToDispatchService() {
        DispatchSessionEntity session = buildSession(DispatchStatus.RETRYING);
        when(dispatchRepository.findExpiredDispatchSessions(any())).thenReturn(List.of(session));

        scheduler.handleExpiredAssignments();

        verify(dispatchService).handleDispatchTimeout(session.getDispatchId());
    }

    @Test
    void handleExpiredAssignments_IgnoresOtherStates() {
        DispatchSessionEntity session = buildSession(DispatchStatus.SEARCHING);
        when(dispatchRepository.findExpiredDispatchSessions(any())).thenReturn(List.of(session));

        scheduler.handleExpiredAssignments();

        verifyNoInteractions(dispatchService);
    }

    private DispatchSessionEntity buildSession(DispatchStatus status) {
        DispatchSessionEntity session = new DispatchSessionEntity();
        session.setDispatchId(UUID.randomUUID());
        session.setRideId(UUID.randomUUID());
        session.setRiderUserId(1L);
        session.setStatus(status);
        session.setCurrentDriverUserId(2L);
        session.setRemainingCandidates("[3,4,5]");
        session.setRetryCount(1);
        session.setCreatedAt(Instant.now().minusSeconds(40));
        session.setExpiresAt(Instant.now().minusSeconds(5));
        session.setUpdatedAt(Instant.now().minusSeconds(5));
        return session;
    }
}
