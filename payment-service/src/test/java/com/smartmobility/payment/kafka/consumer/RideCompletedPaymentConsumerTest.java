package com.smartmobility.payment.kafka.consumer;

import com.smartmobility.payment.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideCompletedPaymentConsumerTest {

    @Mock
    private WalletService walletService;

    private RideCompletedPaymentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RideCompletedPaymentConsumer(new ObjectMapper(), walletService);
    }

    @Test
    void debitsRiderWalletWhenFarePresent() {
        String rideId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        String message = "{\"eventId\":\"evt-1\",\"rideId\":\"" + rideId + "\",\"driverUserId\":10,"
            + "\"riderUserId\":20,\"completedAt\":\"2026-08-11T10:00:00\",\"fare\":150.0}";

        consumer.consume(message);

        verify(walletService).debit(eq(20L), eq(150L), eq("evt-1"), eq(rideId));
    }

    @Test
    void skipsWhenFareIsNull() {
        String message = "{\"eventId\":\"evt-2\",\"rideId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\","
            + "\"driverUserId\":10,\"riderUserId\":20,\"completedAt\":\"2026-08-11T10:00:00\"}";

        consumer.consume(message);

        verifyNoInteractions(walletService);
    }

    @Test
    void skipsWhenFareIsZeroOrNegative() {
        String message = "{\"eventId\":\"evt-3\",\"rideId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\","
            + "\"driverUserId\":10,\"riderUserId\":20,\"completedAt\":\"2026-08-11T10:00:00\",\"fare\":0.0}";

        consumer.consume(message);

        verifyNoInteractions(walletService);
    }
}
