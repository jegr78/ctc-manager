package org.ctc.domain.repository;

import org.ctc.domain.model.RaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceSettingsRepository extends JpaRepository<RaceSettings, UUID> {
}
