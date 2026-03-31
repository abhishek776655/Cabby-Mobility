package com.smartmobility.user_service.strategy;

import com.smartmobility.user_service.entity.Role;

public interface UserRoleStrategy {

    void createRoleSpecificEntity(Long userId);

    Role getRole();
}
