package com.smartmobility.matchmaking.scoring;

import com.smartmobility.matchmaking.config.MatchmakingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeDriverRankingServiceTest {

    private CompositeDriverRankingService rankingService;

    @BeforeEach
    void setUp() {
        MatchmakingProperties properties = new MatchmakingProperties();
        properties.getScoring().setRatingWeight(0.4);
        properties.getScoring().setDistanceWeight(0.6);
        rankingService = new CompositeDriverRankingService(
            new RatingDriverScoringStrategy(), new DistanceDriverScoringStrategy(), properties);
    }

    @Test
    void ranksHighRatingCloseDriverFirst() {
        List<DriverCandidate> candidates = List.of(
            new DriverCandidate(1L, 5.0, 60.0),
            new DriverCandidate(2L, 1.0, 300.0)
        );

        List<Long> ranked = rankingService.rank(candidates);

        assertEquals(List.of(1L, 2L), ranked);
    }

    @Test
    void singleCandidateRanksAlone() {
        List<DriverCandidate> candidates = List.of(new DriverCandidate(1L, 3.0, 120.0));

        assertEquals(List.of(1L), rankingService.rank(candidates));
    }

    @Test
    void emptyCandidatesRanksEmpty() {
        assertEquals(List.of(), rankingService.rank(List.of()));
    }
}
