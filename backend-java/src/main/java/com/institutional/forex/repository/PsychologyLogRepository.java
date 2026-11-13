package com.institutional.forex.repository;

import com.institutional.forex.model.entity.PsychologyLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PsychologyLogRepository extends JpaRepository<PsychologyLogEntity, Long> {
    Optional<PsychologyLogEntity> findByLogId(String logId);
    Optional<PsychologyLogEntity> findByLogDate(String logDate);
    List<PsychologyLogEntity> findAllByOrderByRecordedAtUtcDesc();
}
