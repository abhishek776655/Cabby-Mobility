package com.smartmobility.auth.client;

import com.smartmobility.auth.dto.ApiResponse;
import com.smartmobility.auth.dto.UserCreateRequestDTO;
import com.smartmobility.auth.dto.UserResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserServiceClient {

    @PostMapping("/internal/users")
    ApiResponse<UserResponseDTO> createUser(@RequestBody UserCreateRequestDTO request);
}