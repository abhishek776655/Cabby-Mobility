package com.smartmobility.user_service.mapper;

import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;
import com.smartmobility.user_service.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserMapper {

    public UserEntity toEntity(CreateUserDTO dto, Long userId) {
        return UserEntity.builder()
                .id(userId)
                .email(dto.getEmail())
                .role(dto.getRole())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public UserResponseDTO toDTO(UserEntity entity) {
        return UserResponseDTO.builder()
                .userId(entity.getId())
                .email(entity.getEmail())
                .role(entity.getRole())
                .build();
    }
}
