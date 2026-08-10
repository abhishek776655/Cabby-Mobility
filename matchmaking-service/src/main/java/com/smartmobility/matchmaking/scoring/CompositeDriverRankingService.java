package com.smartmobility.matchmaking.scoring;

import com.smartmobility.matchmaking.config.MatchmakingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CompositeDriverRankingService {

    private final RatingDriverScoringStrategy ratingStrategy;
    private final DistanceDriverScoringStrategy distanceStrategy;
    private final MatchmakingProperties properties;

    public List<Long> rank(List<DriverCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.of(candidates.get(0).driverUserId());
        }

        Map<Long, Double> ratingScores = ratingStrategy.score(candidates);
        Map<Long, Double> distanceScores = distanceStrategy.score(candidates);
        double ratingWeight = properties.getScoring().getRatingWeight();
        double distanceWeight = properties.getScoring().getDistanceWeight();

        return candidates.stream()
            .map(DriverCandidate::driverUserId)
            .sorted(Comparator.comparingDouble((Long driverUserId) ->
                ratingWeight * ratingScores.get(driverUserId) + distanceWeight * distanceScores.get(driverUserId)
            ).reversed())
            .toList();
    }
}
