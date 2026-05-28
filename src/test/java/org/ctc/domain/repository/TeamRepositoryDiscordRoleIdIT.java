package org.ctc.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class TeamRepositoryDiscordRoleIdIT {

	private static final String ROLE_ID = "123456789012345678";

	@Autowired
	private TeamRepository teamRepository;

	@Test
	void givenTeamWithDiscordRoleId_whenSaveAndReload_thenSnowflakePersists() {
		// given
		Team team = new Team("Test-Alpha", "T-ALF");
		team.setDiscordRoleId(ROLE_ID);

		// when
		Team saved = teamRepository.save(team);
		teamRepository.flush();
		Team reloaded = teamRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(reloaded.getDiscordRoleId()).isEqualTo(ROLE_ID);
	}

	@Test
	void givenTeamWithNullDiscordRoleId_whenSave_thenColumnNullable() {
		// given
		Team team = new Team("Test-Bravo", "T-BRV");
		team.setDiscordRoleId(null);

		// when
		Team saved = teamRepository.save(team);
		teamRepository.flush();
		Team reloaded = teamRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(reloaded.getDiscordRoleId()).isNull();
	}

	@Test
	void givenTeamSavedWithRoleId_whenClearRoleId_thenColumnUpdatesToNull() {
		// given
		Team team = new Team("Test-Charlie", "T-CHA");
		team.setDiscordRoleId(ROLE_ID);
		Team saved = teamRepository.save(team);
		teamRepository.flush();

		// when
		saved.setDiscordRoleId(null);
		teamRepository.save(saved);
		teamRepository.flush();
		Team reloaded = teamRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(reloaded.getDiscordRoleId()).isNull();
	}
}
