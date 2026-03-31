package com.smartmobility.user_service.factory;

import com.smartmobility.user_service.entity.Role;
import com.smartmobility.user_service.strategy.UserRoleStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserRoleStrategyFactory {

    private final Map<Role, UserRoleStrategy> strategyMap;

    public UserRoleStrategyFactory(List<UserRoleStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(UserRoleStrategy::getRole, s -> s));
    }

    public UserRoleStrategy getStrategy(Role role) {
        return Optional.ofNullable(strategyMap.get(role))
                .orElseThrow(() -> new RuntimeException("Invalid role: " + role));
    }
}