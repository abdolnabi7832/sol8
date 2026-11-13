package com.institutional.forex.repository;

import com.institutional.forex.model.entity.MarketStructureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {
    Optional<MarketStructureEntity> findByPairAndTimeframe(String pair, String timeframe);
    List<MarketStructureEntity> findByPair(String pair);
}
