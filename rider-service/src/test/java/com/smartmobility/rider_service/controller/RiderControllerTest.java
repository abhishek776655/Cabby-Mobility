package com.smartmobility.rider_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.rider_service.dto.RiderResponseDTO;
import com.smartmobility.rider_service.dto.UpdatePreferencesRequestDTO;
import com.smartmobility.rider_service.service.RiderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiderController.class)
public class RiderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiderService riderService;

    @MockitoBean
    private com.smartmobility.rider_service.service.SavedLocationService savedLocationService;

    @Autowired

    private ObjectMapper objectMapper;

    @Test
    public void whenGetMeWithValidHeader_thenReturnProfile() throws Exception {
        RiderResponseDTO response = RiderResponseDTO.builder()
                .id(1L)
                .userId(100L)
                .rating(4.8)
                .preferredPaymentMethod("CASH")
                .build();

        when(riderService.getRiderByUserId(100L)).thenReturn(response);

        // Assert 200 OK and expected JSON structure.
        // Fails right now because GET /riders/me is not implemented on RiderController!
        mockMvc.perform(get("/riders/me")
                        .header("X-User-Id", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.rating").value(4.8))
                .andExpect(jsonPath("$.preferredPaymentMethod").value("CASH"));
    }

    @Test
    public void whenUpdatePreferencesWithValidBody_thenReturnUpdatedProfile() throws Exception {
        UpdatePreferencesRequestDTO request = new UpdatePreferencesRequestDTO("CARD");
        RiderResponseDTO response = RiderResponseDTO.builder()
                .id(1L)
                .userId(100L)
                .rating(4.8)
                .preferredPaymentMethod("CARD")
                .build();

        when(riderService.updatePreferences(eq(100L), any(UpdatePreferencesRequestDTO.class))).thenReturn(response);

        mockMvc.perform(patch("/riders/me/preferences")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPaymentMethod").value("CARD"));
    }

    @Test
    public void whenUpdatePreferencesWithInvalidBody_thenReturnBadRequest() throws Exception {
        UpdatePreferencesRequestDTO request = new UpdatePreferencesRequestDTO("BITCOIN"); // Invalid Enum value

        mockMvc.perform(patch("/riders/me/preferences")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
