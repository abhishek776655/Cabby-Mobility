package com.smartmobility.payment.service.impl;

import com.smartmobility.payment.entity.WalletEntity;
import com.smartmobility.payment.entity.WalletTransactionEntity;
import com.smartmobility.payment.repository.WalletRepository;
import com.smartmobility.payment.repository.WalletTransactionRepository;
import com.smartmobility.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Override
    @Transactional
    public WalletEntity getOrCreateWallet(Long userId) {
        return walletRepository.findById(userId).orElseGet(() -> walletRepository.save(
                WalletEntity.builder()
                        .userId(userId)
                        .balance(0L)
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    @Override
    @Transactional
    public void debit(Long userId, long amount, String eventId, String rideId) {
        applyTransaction(userId, amount, eventId, rideId, "DEBIT", -amount);
    }

    @Override
    @Transactional
    public void credit(Long userId, long amount, String eventId) {
        applyTransaction(userId, amount, eventId, null, "CREDIT", amount);
    }

    private void applyTransaction(Long userId, long amount, String eventId, String rideId, String type, long balanceDelta) {
        if (transactionRepository.existsByEventId(eventId)) {
            log.info("Transaction for event {} already processed, skipping.", eventId);
            return;
        }

        try {
            WalletEntity wallet = getOrCreateWallet(userId);
            long newBalance = wallet.getBalance() + balanceDelta;
            wallet.setBalance(newBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            WalletTransactionEntity transaction = WalletTransactionEntity.builder()
                    .userId(userId)
                    .rideId(rideId)
                    .eventId(eventId)
                    .type(type)
                    .amount(amount)
                    .balanceAfter(newBalance)
                    .status("COMPLETED")
                    .createdAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(transaction);

            log.info("{} of {} applied to wallet {}, new balance {}", type, amount, userId, newBalance);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event detected during save for event {}: {}", eventId, e.getMessage());
        }
    }

    @Override
    public List<WalletTransactionEntity> getTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
