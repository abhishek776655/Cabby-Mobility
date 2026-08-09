package com.smartmobility.pricing.repository;

import com.smartmobility.pricing.entity.RateCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateCardRepository extends JpaRepository<RateCardEntity, String> {
}
