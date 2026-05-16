package com.smartmobility.matchmaking.scheduler;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import com.smartmobility.matchmaking.redis.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchTimeoutScheduler {

    private final DispatchSessionRepository dispatchRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void handleExpiredAssignments() {
        List<DispatchSessionEntity> expiredSessions =
            dispatchRepository.findExpiredDispatchSessions(Instant.now());

        for (DispatchSessionEntity session : expiredSessions) {
            if (session.getStatus() == DispatchStatus.ASSIGNMENT_SENT &&
                session.getCurrentDriverId() != null) {

                log.info("Handling timeout for dispatch {} driver {}",
                    session.getDispatchId(), session.getCurrentDriverId());

                reservationService.releaseReservation(
                    session.getCurrentDriverId(),
                    session.getDispatchId().toString());

                session.setStatus(DispatchStatus.RETRYING);
                session.setUpdatedAt(Instant.now());
                dispatchRepository.save(session);
            }
        }
    }
}