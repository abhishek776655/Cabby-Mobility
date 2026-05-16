package com.smartmobility.user_service;

import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;
import com.smartmobility.user_service.entity.UserEntity;
import com.smartmobility.user_service.mapper.UserMapper;
import com.smartmobility.user_service.producer.UserEventPublisher;
import com.smartmobility.user_service.repository.UserRepository;
import com.smartmobility.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUserById_shouldReturnUser() {

        // Arrange
        UserEntity user = new UserEntity();
        user.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDTO(user)).thenReturn(
                UserResponseDTO.builder().userId(1L).build()
        );

        // Act
        UserResponseDTO result = userService.getUserById(1L);

        // Assert
        assertEquals(1L, result.getUserId());
    }
}
