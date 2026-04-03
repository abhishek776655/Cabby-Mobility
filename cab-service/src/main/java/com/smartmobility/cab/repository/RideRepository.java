package com.smartmobility.cab.repository;

import com.smartmobility.cab.entity.RideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RideRepository extends JpaRepository<RideEntity, UUID> {
}
