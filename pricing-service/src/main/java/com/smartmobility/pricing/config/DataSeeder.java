package com.smartmobility.pricing.config;

import com.smartmobility.pricing.entity.RateCardEntity;
import com.smartmobility.pricing.repository.RateCardRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final RateCardRepository rateCardRepository;

    @PostConstruct
    public void seedData() {
        if (rateCardRepository.count() == 0) {
            log.info("Seeding default rate cards...");

            // STANDARD vehicle
            rateCardRepository.save(RateCardEntity.builder()
                    .vehicleType("STANDARD")
                    .baseFare(5000)      // 50.00
                    .perKmRate(1500)     // 15.00
                    .perMinRate(200)     // 2.00
                    .minFare(8000)       // 80.00
                    .cancellationFee(3000) // 30.00
                    .active(true)
                    .updatedAt(LocalDateTime.now())
                    .build());

            // PREMIUM vehicle
            rateCardRepository.save(RateCardEntity.builder()
                    .vehicleType("PREMIUM")
                    .baseFare(8000)      // 80.00
                    .perKmRate(2500)     // 25.00
                    .perMinRate(300)     // 3.00
                    .minFare(15000)      // 150.00
                    .cancellationFee(5000) // 50.00
                    .active(true)
                    .updatedAt(LocalDateTime.now())
                    .build());

            // XL vehicle
            rateCardRepository.save(RateCardEntity.builder()
                    .vehicleType("XL")
                    .baseFare(10000)     // 100.00
                    .perKmRate(3000)     // 30.00
                    .perMinRate(400)     // 4.00
                    .minFare(20000)      // 200.00
                    .cancellationFee(6000) // 60.00
                    .active(true)
                    .updatedAt(LocalDateTime.now())
                    .build());
                    
            log.info("Rate cards seeded successfully.");
        }
    }
}
