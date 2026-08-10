package com.smartmobility.matchmaking.scoring;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceDriverScoringStrategyTest {

    private final DistanceDriverScoringStrategy strategy = new DistanceDriverScoringStrategy();

    @Test
    void closestDriverScoresHighestFurthestScoresLowest() {
        List<DriverCandidate> candidates = List.of(
            new DriverCandidate(1L, null, 60.0),
            new DriverCandidate(2L, null, 300.0),
            new DriverCandidate(3L, null, 180.0)
        );

        Map<Long, Double> scores = strategy.score(candidates);

        assertEquals(1.0, scores.get(1L), 0.0001);
        assertEquals(0.0, scores.get(2L), 0.0001);
        assertEquals(0.5, scores.get(3L), 0.0001);
    }

    @Test
    void allEqualEtaScoresEveryoneOne() {
        List<DriverCandidate> candidates = List.of(
            new DriverCandidate(1L, null, 120.0),
            new DriverCandidate(2L, null, 120.0)
        );

        Map<Long, Double> scores = strategy.score(candidates);

        assertEquals(1.0, scores.get(1L), 0.0001);
        assertEquals(1.0, scores.get(2L), 0.0001);
    }
}
