package com.institutional.forex.repository;

import com.institutional.forex.model.entity.UserSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
    Optional<UserSettingsEntity> findByUserIdentifier(String userIdentifier);
}
