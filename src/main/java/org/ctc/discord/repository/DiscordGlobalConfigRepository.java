package org.ctc.discord.repository;

import org.ctc.discord.model.DiscordGlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordGlobalConfigRepository extends JpaRepository<DiscordGlobalConfig, Long> {

	DiscordGlobalConfig findFirstByOrderByIdAsc();
}
