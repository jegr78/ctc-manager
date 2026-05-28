package org.ctc.discord.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DiscordPostRepository
		extends JpaRepository<DiscordPost, Long>, JpaSpecificationExecutor<DiscordPost> {

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>FK columns ({@code matchId}, {@code matchdayId}, {@code raceId}, {@code seasonId},
	 * {@code phaseId}) are {@code @Column UUID} — not JPA associations — so the empty
	 * {@code @EntityGraph} matches the contract.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM DiscordPost e")
	List<DiscordPost> findAllForBackup();

	Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchId(
			String channelId, DiscordPostType postType, UUID matchId);

	Optional<DiscordPost> findByChannelIdAndPostTypeAndRaceId(
			String channelId, DiscordPostType postType, UUID raceId);

	Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(
			String channelId, DiscordPostType postType, UUID seasonId, UUID phaseId);

	Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonIdAndPhaseIdIsNull(
			String channelId, DiscordPostType postType, UUID seasonId);

	Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchdayId(
			String channelId, DiscordPostType postType, UUID matchdayId);

	Optional<DiscordPost> findByPostTypeAndRaceId(DiscordPostType postType, UUID raceId);
}
