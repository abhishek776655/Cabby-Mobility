package com.smartmobility.rider_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.rider_service.dto.CreateSavedLocationRequestDTO;
import com.smartmobility.rider_service.dto.RiderSavedLocationResponseDTO;
import com.smartmobility.rider_service.service.RiderService;
import com.smartmobility.rider_service.service.SavedLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiderController.class)
public class RiderSavedLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiderService riderService;

    @MockitoBean
    private SavedLocationService savedLocationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void whenGetLocationsWithValidHeader_thenReturnList() throws Exception {
        RiderSavedLocationResponseDTO loc = RiderSavedLocationResponseDTO.builder()
                .id(1L)
                .riderId(2L)
                .label("HOME")
                .address("123 Home St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        when(savedLocationService.getSavedLocations(100L)).thenReturn(List.of(loc));

        mockMvc.perform(get("/riders/me/locations")
                        .header("X-User-Id", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("HOME"))
                .andExpect(jsonPath("$[0].address").value("123 Home St"));
    }

    @Test
    public void whenAddLocationWithValidBody_thenReturnCreated() throws Exception {
        CreateSavedLocationRequestDTO request = CreateSavedLocationRequestDTO.builder()
                .label("Home")
                .address("123 Home St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        RiderSavedLocationResponseDTO response = RiderSavedLocationResponseDTO.builder()
                .id(1L)
                .riderId(2L)
                .label("HOME")
                .address("123 Home St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();

        when(savedLocationService.addSavedLocation(eq(100L), any(CreateSavedLocationRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/riders/me/locations")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("HOME"));
    }

    @Test
    public void whenAddLocationWithInvalidCoordinates_thenReturnBadRequest() throws Exception {
        CreateSavedLocationRequestDTO request = CreateSavedLocationRequestDTO.builder()
                .label("Home")
                .address("123 Home St")
                .latitude(95.0) // Out of range latitude (-90 to 90)
                .longitude(-122.4194)
                .build();

        mockMvc.perform(post("/riders/me/locations")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void whenDeleteLocation_thenReturnNoContent() throws Exception {
        doNothing().when(savedLocationService).deleteSavedLocation(100L, 1L);

        mockMvc.perform(delete("/riders/me/locations/1")
                        .header("X-User-Id", "100"))
                .andExpect(status().isNoContent());

        verify(savedLocationService, times(1)).deleteSavedLocation(100L, 1L);
    }
}
