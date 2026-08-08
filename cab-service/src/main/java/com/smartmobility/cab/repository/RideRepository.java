package com.smartmobility.cab.repository;

import com.smartmobility.cab.entity.RideEntity;
import com.smartmobility.cab.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RideRepository extends JpaRepository<RideEntity, UUID> {
    long countByStatusIn(List<RideStatus> statuses);

    @Query("SELECT COUNT(DISTINCT r.riderUserId) FROM RideEntity r WHERE r.status IN :statuses")
    long countDistinctRiderUserIdByStatusIn(List<RideStatus> statuses);

    @Query("SELECT COUNT(DISTINCT r.riderUserId) FROM RideEntity r WHERE r.createdAt >= :since")
    long countDistinctRiderUserIdSince(LocalDateTime since);
}
