package com.smartmobility.cab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.cab.dto.CancelDispatchRequest;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.dto.DriverResponseRequest;
import com.smartmobility.cab.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DispatchController.class)
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispatchService dispatchService;

    @Test
    void handleDriverResponse_Accept_ReturnsSuccess() throws Exception {
        UUID dispatchId = UUID.randomUUID();
        Long driverId = 1L;

        DriverResponseRequest request = new DriverResponseRequest();
        request.setDispatchId(dispatchId);
        request.setDriverId(driverId);
        request.setResponse(DriverResponseRequest.DriverResponse.ACCEPT);

        doNothing().when(dispatchService).handleDriverResponse(eq(dispatchId), eq(driverId), eq(true));

        mockMvc.perform(post("/dispatch/driver-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Assignment accepted"));
    }

    @Test
    void handleDriverResponse_Reject_ReturnsSuccess() throws Exception {
        UUID dispatchId = UUID.randomUUID();
        Long driverId = 1L;

        DriverResponseRequest request = new DriverResponseRequest();
        request.setDispatchId(dispatchId);
        request.setDriverId(driverId);
        request.setResponse(DriverResponseRequest.DriverResponse.REJECT);

        doNothing().when(dispatchService).handleDriverResponse(eq(dispatchId), eq(driverId), eq(false));

        mockMvc.perform(post("/dispatch/driver-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Assignment rejected"));
    }

    @Test
    void handleDriverResponse_MissingDispatchId_ReturnsBadRequest() throws Exception {
        DriverResponseRequest request = new DriverResponseRequest();
        request.setDriverId(1L);
        request.setResponse(DriverResponseRequest.DriverResponse.ACCEPT);

        mockMvc.perform(post("/dispatch/driver-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelDispatch_ReturnsSuccess() throws Exception {
        UUID rideId = UUID.randomUUID();

        CancelDispatchRequest request = new CancelDispatchRequest();
        request.setRideId(rideId);
        request.setReason("User requested cancellation");

        doNothing().when(dispatchService).cancelDispatch(eq(rideId), anyString());

        mockMvc.perform(post("/dispatch/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dispatch cancelled"));
    }

    @Test
    void getDispatchStatus_Found_ReturnsStatus() throws Exception {
        UUID rideId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();

        DispatchStatusResponse response = DispatchStatusResponse.builder()
                .dispatchId(dispatchId)
                .rideId(rideId)
                .status("ASSIGNMENT_SENT")
                .driverId(1L)
                .retryCount(0)
                .build();

        when(dispatchService.getDispatchStatus(rideId)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/dispatch/{rideId}", rideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ASSIGNMENT_SENT"))
                .andExpect(jsonPath("$.data.driverId").value(1));
    }

    @Test
    void getDispatchStatus_NotFound_Returns404() throws Exception {
        UUID rideId = UUID.randomUUID();

        when(dispatchService.getDispatchStatus(rideId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/dispatch/{rideId}", rideId))
                .andExpect(status().isNotFound());
    }
}