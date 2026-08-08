package com.smartmobility.rider_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequestDTO {

    @NotBlank(message = "Preferred payment method cannot be blank")
    @Pattern(regexp = "CASH|CARD|WALLET", message = "Preferred payment method must be CASH, CARD, or WALLET")
    private String preferredPaymentMethod;
}
