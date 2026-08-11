package com.smartmobility.payment.service.impl;

import com.smartmobility.payment.entity.WalletEntity;
import com.smartmobility.payment.entity.WalletTransactionEntity;
import com.smartmobility.payment.repository.WalletRepository;
import com.smartmobility.payment.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    private WalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(walletRepository, transactionRepository);
    }

    @Test
    void getOrCreateWalletReturnsExistingWallet() {
        WalletEntity existing = WalletEntity.builder().userId(1L).balance(500L).updatedAt(LocalDateTime.now()).build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(existing));

        WalletEntity result = walletService.getOrCreateWallet(1L);

        assertEquals(500L, result.getBalance());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getOrCreateWalletCreatesNewWalletWithZeroBalanceWhenMissing() {
        when(walletRepository.findById(1L)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletEntity result = walletService.getOrCreateWallet(1L);

        assertEquals(1L, result.getUserId());
        assertEquals(0L, result.getBalance());
    }

    @Test
    void debitReducesBalanceAndRecordsTransaction() {
        WalletEntity existing = WalletEntity.builder().userId(1L).balance(1000L).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.existsByEventId("evt-1")).thenReturn(false);
        when(walletRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.debit(1L, 300L, "evt-1", "ride-1");

        ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertEquals(700L, walletCaptor.getValue().getBalance());

        ArgumentCaptor<WalletTransactionEntity> txnCaptor = ArgumentCaptor.forClass(WalletTransactionEntity.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertEquals("DEBIT", txnCaptor.getValue().getType());
        assertEquals(300L, txnCaptor.getValue().getAmount());
        assertEquals(700L, txnCaptor.getValue().getBalanceAfter());
        assertEquals("ride-1", txnCaptor.getValue().getRideId());
    }

    @Test
    void debitAllowsNegativeBalance() {
        WalletEntity existing = WalletEntity.builder().userId(1L).balance(100L).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.existsByEventId("evt-1")).thenReturn(false);
        when(walletRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.debit(1L, 300L, "evt-1", "ride-1");

        ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertEquals(-200L, walletCaptor.getValue().getBalance());
    }

    @Test
    void creditIncreasesBalance() {
        WalletEntity existing = WalletEntity.builder().userId(1L).balance(100L).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.existsByEventId("topup-1")).thenReturn(false);
        when(walletRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.credit(1L, 500L, "topup-1");

        ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertEquals(600L, walletCaptor.getValue().getBalance());

        ArgumentCaptor<WalletTransactionEntity> txnCaptor = ArgumentCaptor.forClass(WalletTransactionEntity.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertEquals("CREDIT", txnCaptor.getValue().getType());
        assertNull(txnCaptor.getValue().getRideId());
    }

    @Test
    void duplicateEventIdIsSkipped() {
        when(transactionRepository.existsByEventId("evt-1")).thenReturn(true);

        walletService.debit(1L, 300L, "evt-1", "ride-1");

        verifyNoInteractions(walletRepository);
        verify(transactionRepository, never()).save(any());
    }
}
