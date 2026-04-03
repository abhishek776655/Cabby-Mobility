package com.smartmobility.auth.repository;

import com.smartmobility.auth.entity.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthCredentialRepository extends JpaRepository<AuthCredential, Long> {

    Optional<AuthCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}
