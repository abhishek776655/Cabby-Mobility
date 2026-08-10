package com.smartmobility.matchmaking.scoring;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DistanceDriverScoringStrategy implements DriverScoringStrategy {

    @Override
    public Map<Long, Double> score(List<DriverCandidate> candidates) {
        double minEta = candidates.stream().mapToDouble(DriverCandidate::etaSeconds).min().orElse(0.0);
        double maxEta = candidates.stream().mapToDouble(DriverCandidate::etaSeconds).max().orElse(0.0);
        double range = maxEta - minEta;

        return candidates.stream().collect(Collectors.toMap(
            DriverCandidate::driverUserId,
            c -> range == 0.0 ? 1.0 : (maxEta - c.etaSeconds()) / range
        ));
    }
}
