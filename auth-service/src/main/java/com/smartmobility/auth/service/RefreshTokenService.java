package com.smartmobility.auth.service;

import com.smartmobility.auth.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken create(Long userId);

    RefreshToken validate(String token);

    void revoke(String token);

    void revokeAll(Long userId);
}
