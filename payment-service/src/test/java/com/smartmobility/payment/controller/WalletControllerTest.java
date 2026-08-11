package com.smartmobility.payment.controller;

import com.smartmobility.payment.dto.ApiResponse;
import com.smartmobility.payment.dto.TopupRequest;
import com.smartmobility.payment.dto.WalletBalanceResponse;
import com.smartmobility.payment.entity.WalletEntity;
import com.smartmobility.payment.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    private WalletController controller;

    @BeforeEach
    void setUp() {
        controller = new WalletController(walletService);
    }

    @Test
    void getBalanceReturnsWalletBalance() {
        when(walletService.getOrCreateWallet(1L)).thenReturn(
            WalletEntity.builder().userId(1L).balance(750L).updatedAt(LocalDateTime.now()).build());

        ResponseEntity<ApiResponse<WalletBalanceResponse>> response = controller.getBalance(1L);

        assertEquals(750L, response.getBody().getData().getBalance());
    }

    @Test
    void topupCreditsWallet() {
        TopupRequest request = TopupRequest.builder().amount(500L).referenceId("ref-1").build();

        controller.topup(1L, request);

        verify(walletService).credit(1L, 500L, "ref-1");
    }

    @Test
    void getTransactionsDelegatesToService() {
        when(walletService.getTransactions(1L)).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<?>>> response = (ResponseEntity) controller.getTransactions(1L);

        assertEquals(true, response.getBody().isSuccess());
    }
}
