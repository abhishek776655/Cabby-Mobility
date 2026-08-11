package com.smartmobility.payment.controller;

import com.smartmobility.payment.dto.ApiResponse;
import com.smartmobility.payment.dto.TopupRequest;
import com.smartmobility.payment.dto.WalletBalanceResponse;
import com.smartmobility.payment.entity.WalletTransactionEntity;
import com.smartmobility.payment.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(@PathVariable Long userId) {
        var wallet = walletService.getOrCreateWallet(userId);
        return ResponseEntity.ok(ApiResponse.success(
                WalletBalanceResponse.builder().userId(wallet.getUserId()).balance(wallet.getBalance()).build()));
    }

    @PostMapping("/{userId}/topup")
    public ResponseEntity<ApiResponse<Void>> topup(@PathVariable Long userId, @Valid @RequestBody TopupRequest request) {
        walletService.credit(userId, request.getAmount(), request.getReferenceId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionEntity>>> getTransactions(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getTransactions(userId)));
    }
}
