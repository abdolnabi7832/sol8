package com.institutional.forex.repository;

import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEventEntity, Long> {

    Optional<EconomicEventEntity> findByEventId(String eventId);

    Optional<EconomicEventEntity> findByCurrencyAndEventNameAndEventDateStrAndTimeUtc(
            String currency, String eventName, String eventDateStr, String timeUtc);

    List<EconomicEventEntity> findByCurrencyOrderByExactEventTimestampUtcAsc(String currency);

    List<EconomicEventEntity> findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(
            String currency, ImpactLevel impact);

    List<EconomicEventEntity> findByExactEventTimestampUtcBetweenOrderByExactEventTimestampUtcAsc(
            LocalDateTime startUtc, LocalDateTime endUtc);

    List<EconomicEventEntity> findByCurrencyAndExactEventTimestampUtcBetweenOrderByExactEventTimestampUtcAsc(
            String currency, LocalDateTime startUtc, LocalDateTime endUtc);

    List<EconomicEventEntity> findByCurrencyAndMappedStandardIndicatorOrderByExactEventTimestampUtcDesc(
            String currency, String mappedStandardIndicator);

    @Query("SELECT e FROM EconomicEventEntity e WHERE e.currency = :currency AND e.mappedStandardIndicator = :indicator AND e.exactEventTimestampUtc > :referenceTime ORDER BY e.exactEventTimestampUtc ASC")
    List<EconomicEventEntity> findUpcomingByCurrencyAndIndicator(
            @Param("currency") String currency,
            @Param("indicator") String indicator,
            @Param("referenceTime") LocalDateTime referenceTime);

    @Query("SELECT e FROM EconomicEventEntity e WHERE e.currency = :currency AND e.mappedStandardIndicator = :indicator AND e.exactEventTimestampUtc <= :referenceTime ORDER BY e.exactEventTimestampUtc DESC")
    List<EconomicEventEntity> findPastByCurrencyAndIndicator(
            @Param("currency") String currency,
            @Param("indicator") String indicator,
            @Param("referenceTime") LocalDateTime referenceTime);
}
