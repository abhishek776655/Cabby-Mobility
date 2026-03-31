package com.smartmobility.user_service.controller;

import com.smartmobility.user_service.config.ApiResponseBuilder;
import com.smartmobility.user_service.dto.ApiResponse;
import com.smartmobility.user_service.dto.CreateUserDTO;
import com.smartmobility.user_service.dto.UserResponseDTO;
import com.smartmobility.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @RequestBody @Valid CreateUserDTO dto) {

        UserResponseDTO response = userService.createUser(dto);

        return ResponseEntity
                .created(URI.create("/internal/users/" + response.getUserId()))
                .body(ApiResponseBuilder.success(response,201));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getById(@PathVariable Long id) {

        UserResponseDTO response = userService.getUserById(id);

        return ResponseEntity.ok(
                ApiResponseBuilder.success(userService.getUserById(id), 200)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUser(
            @RequestParam(required = false) String email) {

        if (email != null) {

            UserResponseDTO response = userService.getUserByEmail(email);

            return ResponseEntity.ok(
                   ApiResponseBuilder.success(userService.getUserByEmail(email), 200)
            );
        }

        throw new IllegalArgumentException("Email query param is required");
    }
}