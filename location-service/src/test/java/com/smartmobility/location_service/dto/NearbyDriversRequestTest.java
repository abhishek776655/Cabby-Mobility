package com.smartmobility.location_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyDriversRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private NearbyDriversRequest request(double radiusKm, int limit) {
        NearbyDriversRequest request = new NearbyDriversRequest();
        request.setLat(28.6139);
        request.setLng(77.2090);
        request.setRadiusKm(radiusKm);
        request.setLimit(limit);
        return request;
    }

    @Test
    void withinBoundsIsValid() {
        Set<ConstraintViolation<NearbyDriversRequest>> violations = VALIDATOR.validate(request(5.0, 10));
        assertEquals(0, violations.size());
    }

    @Test
    void radiusAboveCapIsRejected() {
        Set<ConstraintViolation<NearbyDriversRequest>> violations = VALIDATOR.validate(request(999999.0, 10));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("radiusKm")));
    }

    @Test
    void limitAboveCapIsRejected() {
        Set<ConstraintViolation<NearbyDriversRequest>> violations = VALIDATOR.validate(request(5.0, 999999999));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("limit")));
    }
}
