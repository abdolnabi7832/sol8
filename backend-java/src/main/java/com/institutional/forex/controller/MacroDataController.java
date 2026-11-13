package com.institutional.forex.controller;

import com.institutional.forex.model.dto.MacroIndicatorDto;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.MacroIndicatorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MacroDataController: REST API endpoints delivering institutional macro indicators,
 * pillar weights, and currency matrix.
 */
@RestController
@RequestMapping("/api/v1/macro")
public class MacroDataController {

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final OmniIndicatorRegistry indicatorRegistry;

    public MacroDataController(MacroIndicatorRepository macroIndicatorRepository,
                               OmniIndicatorRegistry indicatorRegistry) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.indicatorRegistry = indicatorRegistry;
    }

    /**
     * Retrieves all 20 macro indicators for a specific currency.
     */
    @GetMapping("/indicators/{currency}")
    public ResponseEntity<List<MacroIndicatorDto>> getIndicatorsByCurrency(@PathVariable String currency) {
        String ccy = currency.toUpperCase();
        if (!indicatorRegistry.isCurrencySupported(ccy)) {
            return ResponseEntity.badRequest().build();
        }

        List<MacroIndicatorEntity> entities = macroIndicatorRepository
                .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);

        List<MacroIndicatorDto> dtos = entities.stream()
                .map(MacroIndicatorDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves all macro indicators across all currencies, optionally filtered by pillar number.
     */
    @GetMapping("/indicators")
    public ResponseEntity<List<MacroIndicatorDto>> getAllIndicators(
            @RequestParam(required = false) Integer pillarNumber) {

        List<MacroIndicatorEntity> entities;
        if (pillarNumber != null) {
            entities = macroIndicatorRepository.findByPillarNumber(pillarNumber);
        } else {
            entities = macroIndicatorRepository.findAll();
        }

        List<MacroIndicatorDto> dtos = entities.stream()
                .map(MacroIndicatorDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves metadata for all supported G8 institutional currencies.
     */
    @GetMapping("/currencies")
    public ResponseEntity<Map<String, OmniIndicatorRegistry.CountryMetadata>> getSupportedCurrencies() {
        return ResponseEntity.ok(OmniIndicatorRegistry.COUNTRY_MAP);
    }

    /**
     * Retrieves metadata and institutional weights for the 6 Anton Kreil Macro Pillars.
     */
    @GetMapping("/pillars")
    public ResponseEntity<Map<Integer, OmniIndicatorRegistry.PillarMetadata>> getMacroPillars() {
        return ResponseEntity.ok(OmniIndicatorRegistry.PILLAR_MAP);
    }

    /**
     * Retrieves standard 20 indicator canonical specifications.
     */
    @GetMapping("/canonical-specs")
    public ResponseEntity<List<OmniIndicatorRegistry.IndicatorSpec>> getCanonicalSpecs() {
        return ResponseEntity.ok(indicatorRegistry.getAllStandardIndicators());
    }
}
