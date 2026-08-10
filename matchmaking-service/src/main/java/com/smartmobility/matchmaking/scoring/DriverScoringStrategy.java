package com.smartmobility.matchmaking.scoring;

import java.util.List;
import java.util.Map;

public interface DriverScoringStrategy {
    /**
     * Scores every candidate in one pass so strategies that need the whole batch
     * (e.g. min-max normalizing ETA) can do so. Returned scores are normalized to [0.0, 1.0].
     */
    Map<Long, Double> score(List<DriverCandidate> candidates);
}
