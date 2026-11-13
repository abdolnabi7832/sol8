package com.institutional.forex.repository;

import com.institutional.forex.model.entity.TradeJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeJournalRepository extends JpaRepository<TradeJournalEntity, Long> {
    Optional<TradeJournalEntity> findByJournalId(String journalId);
    List<TradeJournalEntity> findAllByOrderByCreatedAtUtcDesc();
}
