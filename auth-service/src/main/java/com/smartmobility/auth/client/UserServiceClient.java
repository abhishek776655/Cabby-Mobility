package com.smartmobility.auth.client;

import com.smartmobility.auth.dto.ApiResponse;
import com.smartmobility.auth.dto.UserCreateRequestDTO;
import com.smartmobility.auth.dto.UserResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserServiceClient {

    @PostMapping("/internal/users")
    ApiResponse<UserResponseDTO> createUser(@RequestBody UserCreateRequestDTO request);

    @GetMapping("/internal/users")
    ApiResponse<UserResponseDTO> findByEmail(@RequestParam("email") String email);

    @GetMapping("/internal/users/{userId}")
    ApiResponse<UserResponseDTO> findByUserId(@PathVariable("userId") Long userId);
}
