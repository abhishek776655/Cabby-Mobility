package com.smartmobility.user;

import com.smartmobility.user.dto.CreateUserDTO;
import com.smartmobility.user.dto.UserResponseDTO;
import com.smartmobility.user.entity.UserEntity;
import com.smartmobility.user.mapper.UserMapper;
import com.smartmobility.user.producer.UserEventPublisher;
import com.smartmobility.user.repository.UserRepository;
import com.smartmobility.user.service.impl.UserServiceImpl;
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
