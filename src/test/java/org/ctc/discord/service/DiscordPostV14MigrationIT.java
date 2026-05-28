package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostV14MigrationIT {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestHelper helper;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	SeasonPhaseRepository seasonPhaseRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@PersistenceContext
	EntityManager entityManager;

	@BeforeEach
	void resetState() {
		discordPostRepository.deleteAll();
	}

	@Test
	void givenSpringContext_whenStartup_thenV14HasAppliedAndPhaseIdColumnExists() {
		Integer columnExists = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
						+ "WHERE TABLE_NAME = 'DISCORD_POST' AND COLUMN_NAME = 'PHASE_ID'",
				Integer.class);
		assertThat(columnExists).isNotNull().isEqualTo(1);
	}

	@Test
	void givenSeasonPhase_whenInsertDiscordPostWithPhaseId_thenSucceeds() {
		Season season = helper.createSeason("V14 Season A");
		SeasonPhase phase = season.getPhases().get(0);

		DiscordPost post = newPost(season.getId(), phase.getId());
		discordPostRepository.save(post);

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM discord_post WHERE phase_id = ?",
				Integer.class, phase.getId());
		assertThat(count).isNotNull().isEqualTo(1);
	}

	@Test
	void givenPostWithoutPhase_whenInsert_thenSucceedsWithNullPhaseId() {
		Season season = helper.createSeason("V14 Season B");

		DiscordPost post = newPost(season.getId(), null);
		discordPostRepository.save(post);

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM discord_post WHERE season_id = ? AND phase_id IS NULL",
				Integer.class, season.getId());
		assertThat(count).isNotNull().isEqualTo(1);
	}

	@Test
	void givenPostWithPhase_whenDeletePhase_thenPhaseIdSetToNullAndPostPreserved() {
		Season season = helper.createSeason("V14 Season C");
		SeasonPhase phase = season.getPhases().get(0);
		UUID phaseId = phase.getId();
		DiscordPost post = newPost(season.getId(), phaseId);
		DiscordPost saved = discordPostRepository.save(post);

		entityManager.flush();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("DELETE FROM season_phases WHERE id = ?");
			ps.setObject(1, phaseId, Types.OTHER);
			return ps;
		});
		entityManager.clear();

		DiscordPost reloaded = discordPostRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getPhaseId()).isNull();
		assertThat(reloaded.getSeasonId()).isEqualTo(season.getId());
	}

	private DiscordPost newPost(UUID seasonId, UUID phaseId) {
		DiscordPost post = new DiscordPost();
		post.setChannelId("v14-chan");
		post.setMessageId("v14-msg-" + UUID.randomUUID().toString().substring(0, 8));
		post.setWebhookId("v14");
		post.setWebhookToken("tok-v14");
		post.setPostType(DiscordPostType.STANDINGS);
		post.setSeasonId(seasonId);
		post.setPhaseId(phaseId);
		post.setPostedAt(LocalDateTime.now());
		return post;
	}
}
