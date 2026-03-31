package com.smartmobility.user_service.strategy;


import com.smartmobility.user_service.entity.DriverEntity;
import com.smartmobility.user_service.entity.Role;
import com.smartmobility.user_service.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DriverStrategy implements UserRoleStrategy {

    private final DriverRepository driverRepository;

    @Override
    public void createRoleSpecificEntity(Long userId) {

        DriverEntity driver = DriverEntity.builder()
                .userId(userId)
                .available(true)
                .rating(5.0)
                .build();

        driverRepository.save(driver);
    }

    @Override
    public Role getRole() {
        return Role.DRIVER;
    }
}
