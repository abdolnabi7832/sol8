package com.institutional.forex.repository;

import com.institutional.forex.model.entity.ActiveTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveTradeRepository extends JpaRepository<ActiveTradeEntity, Long> {
    List<ActiveTradeEntity> findByStatus(String status);
    Optional<ActiveTradeEntity> findByTradeId(String tradeId);
    List<ActiveTradeEntity> findByPairAndStatus(String pair, String status);
}
