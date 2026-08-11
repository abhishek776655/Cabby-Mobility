package com.smartmobility.payment.service;

import com.smartmobility.payment.entity.WalletEntity;
import com.smartmobility.payment.entity.WalletTransactionEntity;

import java.util.List;

public interface WalletService {
    WalletEntity getOrCreateWallet(Long userId);
    void debit(Long userId, long amount, String eventId, String rideId);
    void credit(Long userId, long amount, String eventId);
    List<WalletTransactionEntity> getTransactions(Long userId);
}
