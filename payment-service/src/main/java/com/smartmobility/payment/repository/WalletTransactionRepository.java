package com.smartmobility.payment.repository;

import com.smartmobility.payment.entity.WalletTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, java.util.UUID> {
    boolean existsByEventId(String eventId);
    Optional<WalletTransactionEntity> findByEventId(String eventId);
    List<WalletTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
