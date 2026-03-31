package com.smartmobility.user_service;

import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;
import com.smartmobility.user_service.entity.Role;
import com.smartmobility.user_service.entity.UserEntity;
import com.smartmobility.user_service.factory.UserRoleStrategyFactory;
import com.smartmobility.user_service.mapper.UserMapper;
import com.smartmobility.user_service.repository.UserRepository;
import com.smartmobility.user_service.service.impl.UserServiceImpl;
import com.smartmobility.user_service.strategy.UserRoleStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleStrategyFactory strategyFactory;

    @Mock
    private UserRoleStrategy strategy;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createUser_shouldCreateUserSuccessfully() {

        // Arrange
        CreateUserDTO dto = new CreateUserDTO();
        dto.setEmail("test@mail.com");
        dto.setRole(Role.RIDER);

        UserEntity userEntity = new UserEntity();
        userEntity.setId(1L);

        UserResponseDTO responseDTO = UserResponseDTO.builder()
                .userId(1L)
                .email("test@mail.com")
                .role(Role.RIDER)
                .build();

        when(userMapper.toEntity(any(), any())).thenReturn(userEntity);
        when(userRepository.save(any())).thenReturn(userEntity);
        when(strategyFactory.getStrategy(Role.RIDER)).thenReturn(strategy);
        when(userMapper.toDTO(userEntity)).thenReturn(responseDTO);

        // Act
        UserResponseDTO result = userService.createUser(dto);

        // Assert
        assertNotNull(result);
        assertEquals("test@mail.com", result.getEmail());

        verify(userRepository).save(any());
        verify(strategy).createRoleSpecificEntity(any());
    }

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