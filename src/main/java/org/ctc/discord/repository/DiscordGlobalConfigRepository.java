package org.ctc.discord.repository;

import java.util.List;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiscordGlobalConfigRepository extends JpaRepository<DiscordGlobalConfig, Long> {

	DiscordGlobalConfig findFirstByOrderByIdAsc();

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code DiscordGlobalConfig} has no JPA associations; the empty
	 * {@code @EntityGraph} keeps the contract uniform across all backup finders.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM DiscordGlobalConfig e")
	List<DiscordGlobalConfig> findAllForBackup();
}
