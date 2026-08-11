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

    /**
     * Scoped to the caller's own wallet via X-User-Id (gateway-injected from the JWT, same
     * ownership convention as rider-service's /riders/me) rather than an arbitrary path userId —
     * a path-param version would let any authenticated rider read/top-up anyone else's wallet.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(@RequestHeader("X-User-Id") Long userId) {
        var wallet = walletService.getOrCreateWallet(userId);
        return ResponseEntity.ok(ApiResponse.success(
                WalletBalanceResponse.builder().userId(wallet.getUserId()).balance(wallet.getBalance()).build()));
    }

    @PostMapping("/me/topup")
    public ResponseEntity<ApiResponse<Void>> topup(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody TopupRequest request) {
        walletService.credit(userId, request.getAmount(), request.getReferenceId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionEntity>>> getTransactions(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getTransactions(userId)));
    }
}
