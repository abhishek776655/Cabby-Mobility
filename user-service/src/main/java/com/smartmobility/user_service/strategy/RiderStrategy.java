package com.smartmobility.user_service.strategy;


import com.smartmobility.user_service.entity.RiderEntity;
import com.smartmobility.user_service.entity.Role;
import com.smartmobility.user_service.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiderStrategy implements UserRoleStrategy {

    private final RiderRepository riderRepository;

    @Override
    public void createRoleSpecificEntity(Long userId) {

        RiderEntity rider = RiderEntity.builder()
                .userId(userId)
                .rating(5.0)
                .build();

        riderRepository.save(rider);
    }

    @Override
    public Role getRole() {
        return Role.RIDER;
    }
}