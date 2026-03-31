package com.smartmobility.user_service.service.impl;

import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;
import com.smartmobility.user_service.entity.UserEntity;
import com.smartmobility.user_service.factory.UserRoleStrategyFactory;
import com.smartmobility.user_service.mapper.UserMapper;
import com.smartmobility.user_service.repository.UserRepository;
import com.smartmobility.user_service.service.UserService;
import com.smartmobility.user_service.strategy.UserRoleStrategy;
import com.smartmobility.user_service.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserRoleStrategyFactory strategyFactory;
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Override
    public UserResponseDTO createUser(CreateUserDTO dto) {

        log.info("Creating user with email: {}", dto.getEmail());

        Long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

        UserEntity user = userMapper.toEntity(dto, userId);

        userRepository.save(user);

        log.info("User saved with id: {}", userId);

        UserRoleStrategy strategy = strategyFactory.getStrategy(dto.getRole());
        strategy.createRoleSpecificEntity(userId);

        log.info("Role-specific entity created for role: {}", dto.getRole());

        return userMapper.toDTO(user);
    }

    @Override
    public UserResponseDTO getUserByEmail(String email) {

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return userMapper.toDTO(user);
    }

    @Override
    public UserResponseDTO getUserById(Long id) {

        log.info("Fetching user with id: {}", id);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new UserNotFoundException("User not found");
                });

        return userMapper.toDTO(user);
    }
}