package com.smartmobility.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.pricing.dto.FareFinalizeRequest;
import com.smartmobility.pricing.dto.FareQuoteRequest;
import com.smartmobility.pricing.dto.FareQuoteResponse;
import com.smartmobility.pricing.repository.FareEstimateRepository;
import com.smartmobility.pricing.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PricingControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PricingServiceImpl pricingService;

    @Mock
    private FareEstimateRepository fareEstimateRepository;

    @InjectMocks
    private PricingController pricingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pricingController).build();
    }

    @Test
    void testQuote() throws Exception {
        FareQuoteRequest request = new FareQuoteRequest(12.0, 77.0, 12.1, 77.1, "STANDARD", null);
        FareQuoteResponse response = FareQuoteResponse.builder()
                .estimateId(UUID.randomUUID())
                .currency("INR")
                .estimateSource("VALHALLA")
                .build();

        when(pricingService.quote(any(FareQuoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/internal/fares/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimateSource").value("VALHALLA"));
    }
}
