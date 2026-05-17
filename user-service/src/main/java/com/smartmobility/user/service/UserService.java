package com.smartmobility.user.service;


import com.smartmobility.user.dto.CreateUserDTO;
import com.smartmobility.user.dto.UserResponseDTO;

public interface UserService {

    UserResponseDTO createUser(CreateUserDTO dto);

    UserResponseDTO getUserByEmail(String email);

    UserResponseDTO getUserById(Long id);
}