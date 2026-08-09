package com.smartmobility.matchmaking.scheduler;

import com.smartmobility.matchmaking.domain.DispatchStatus;
import com.smartmobility.matchmaking.entity.DispatchSessionEntity;
import com.smartmobility.matchmaking.repository.DispatchSessionRepository;
import com.smartmobility.matchmaking.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchTimeoutScheduler {

    private final DispatchSessionRepository dispatchRepository;
    private final DispatchService dispatchService;

    @Scheduled(fixedDelay = 5000)
    public void handleExpiredAssignments() {
        List<DispatchSessionEntity> expiredSessions =
            dispatchRepository.findExpiredDispatchSessions(Instant.now());

        for (DispatchSessionEntity session : expiredSessions) {
            if (session.getStatus() == DispatchStatus.WIDENING_SEARCH) {
                log.info("Retrying wider search for dispatch {} (radius tier {})",
                    session.getDispatchId(), session.getRadiusSweepIndex());

                dispatchService.retryWiderSearch(session.getDispatchId());
            } else if (session.getStatus() == DispatchStatus.ASSIGNMENT_SENT ||
                session.getStatus() == DispatchStatus.RETRYING) {

                log.info("Handling timeout for dispatch {} driver {}",
                    session.getDispatchId(), session.getCurrentDriverUserId());

                dispatchService.handleDispatchTimeout(session.getDispatchId());
            }
        }
    }
}
