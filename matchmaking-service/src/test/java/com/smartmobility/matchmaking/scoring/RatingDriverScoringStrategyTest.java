package com.smartmobility.matchmaking.scoring;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingDriverScoringStrategyTest {

    private final RatingDriverScoringStrategy strategy = new RatingDriverScoringStrategy();

    @Test
    void scoresRatingAsFractionOfFivePointScale() {
        List<DriverCandidate> candidates = List.of(
            new DriverCandidate(1L, 5.0, 120.0),
            new DriverCandidate(2L, 2.5, 90.0)
        );

        Map<Long, Double> scores = strategy.score(candidates);

        assertEquals(1.0, scores.get(1L), 0.0001);
        assertEquals(0.5, scores.get(2L), 0.0001);
    }

    @Test
    void missingRatingDefaultsToZero() {
        List<DriverCandidate> candidates = List.of(new DriverCandidate(1L, null, 120.0));

        Map<Long, Double> scores = strategy.score(candidates);

        assertEquals(0.0, scores.get(1L), 0.0001);
    }
}
