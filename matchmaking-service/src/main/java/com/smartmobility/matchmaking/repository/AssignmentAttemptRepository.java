package com.smartmobility.matchmaking.repository;

import com.smartmobility.matchmaking.entity.AssignmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttempt, Long> {
    List<AssignmentAttempt> findByRideId(UUID rideId);
}