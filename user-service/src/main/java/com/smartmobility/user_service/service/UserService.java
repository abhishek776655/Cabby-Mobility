package com.smartmobility.user_service.service;


import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;

public interface UserService {

    UserResponseDTO createUser(CreateUserDTO dto);

    UserResponseDTO getUserByEmail(String email);

    UserResponseDTO getUserById(Long id);
}