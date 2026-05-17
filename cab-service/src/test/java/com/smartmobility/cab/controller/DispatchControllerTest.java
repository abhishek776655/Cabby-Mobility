package com.smartmobility.cab.controller;

import com.smartmobility.cab.dto.CancelDispatchRequest;
import com.smartmobility.cab.dto.DispatchStatusResponse;
import com.smartmobility.cab.dto.DriverResponseRequest;
import com.smartmobility.cab.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

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

    @MockitoBean
    private DispatchService dispatchService;

    @Test
    void handleDriverResponse_Accept_ReturnsSuccess() throws Exception {
        UUID dispatchId = UUID.randomUUID();
        Long driverUserId = 1L;

        DriverResponseRequest request = new DriverResponseRequest();
        request.setDispatchId(dispatchId);
        request.setDriverUserId(driverUserId);
        request.setResponse(DriverResponseRequest.DriverResponse.ACCEPT);

        doNothing().when(dispatchService).handleDriverResponse(eq(dispatchId), eq(driverUserId), eq(true));

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
        Long driverUserId = 1L;

        DriverResponseRequest request = new DriverResponseRequest();
        request.setDispatchId(dispatchId);
        request.setDriverUserId(driverUserId);
        request.setResponse(DriverResponseRequest.DriverResponse.REJECT);

        doNothing().when(dispatchService).handleDriverResponse(eq(dispatchId), eq(driverUserId), eq(false));

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
        request.setDriverUserId(1L);
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
                .driverUserId(1L)
                .retryCount(0)
                .build();

        when(dispatchService.getDispatchStatus(rideId)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/dispatch/{rideId}", rideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ASSIGNMENT_SENT"))
                .andExpect(jsonPath("$.data.driverUserId").value(1));
    }

    @Test
    void getDispatchStatus_NotFound_Returns404() throws Exception {
        UUID rideId = UUID.randomUUID();

        when(dispatchService.getDispatchStatus(rideId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/dispatch/{rideId}", rideId))
                .andExpect(status().isNotFound());
    }
}
