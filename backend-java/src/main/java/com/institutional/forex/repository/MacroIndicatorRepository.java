package com.institutional.forex.repository;

import com.institutional.forex.model.entity.MacroIndicatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MacroIndicatorRepository extends JpaRepository<MacroIndicatorEntity, Long> {

    List<MacroIndicatorEntity> findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(String currency);

    Optional<MacroIndicatorEntity> findByCurrencyAndIndicatorName(String currency, String indicatorName);

    List<MacroIndicatorEntity> findByPillarNumber(Integer pillarNumber);

    List<MacroIndicatorEntity> findByIndicatorName(String indicatorName);

    boolean existsByCurrencyAndIndicatorName(String currency, String indicatorName);
}
