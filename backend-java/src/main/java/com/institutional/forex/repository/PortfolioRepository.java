package com.institutional.forex.repository;

import com.institutional.forex.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
    Optional<PortfolioEntity> findFirstByOrderByUpdatedAtUtcDesc();
}
