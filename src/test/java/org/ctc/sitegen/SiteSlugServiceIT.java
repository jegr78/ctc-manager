package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SiteSlugRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.sitegen.model.SiteSlugs.SharedSlug;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class SiteSlugServiceIT {

	@Autowired private SiteSlugService siteSlugService;
	@Autowired private SiteSlugRepository siteSlugRepository;
	@Autowired private TeamRepository teamRepository;
	@Autowired private DriverRepository driverRepository;
	@Autowired private TestHelper testHelper;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private final String base = "test-ab-" + id;
	private final List<Team> teams = new ArrayList<>();
	private final List<Driver> drivers = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		siteSlugRepository.deleteAll(siteSlugRepository.findAll().stream()
				.filter(row -> row.getBaseSlug().startsWith(base)).toList());
		teamRepository.deleteAll(teams);
		driverRepository.deleteAll(drivers);
	}

	@Test
	void givenTwoUnstoredTeamsSharingASlug_whenAllocated_thenBothGetSuffixesAndTheSharedSlugListsThem() {
		// given
		Team underscore = team("Test_AB_" + id);
		Team dash = team("Test-AB-" + id);

		// when
		var slugs = siteSlugService.allocate();

		// then
		assertThat(List.of(slugs.team(underscore.getId()), slugs.team(dash.getId())))
				.as("suffixed slugs in creation order").containsExactly(base + "-2", base + "-3");
		assertThat(slugs.shared(SiteSlugKind.TEAM)).as("the shared slug lists both teams")
				.contains(new SharedSlug(SiteSlugKind.TEAM, base, List.of(underscore.getId(), dash.getId())));
	}

	@Test
	void givenTeamWithStoredSlug_whenACollidingTeamIsAdded_thenTheFirstKeepsItsSlugAndTheNewOneGetsTheNextSuffix() {
		// given
		Team first = team("Test_AB_" + id);
		siteSlugService.allocate();
		Team second = team("Test-AB-" + id);

		// when
		var slugs = siteSlugService.allocate();

		// then
		assertThat(slugs.team(first.getId())).as("existing URL keeps its owner").isEqualTo(base);
		assertThat(slugs.team(second.getId())).as("new colliding team").isEqualTo(base + "-2");
		assertThat(slugs.shared(SiteSlugKind.TEAM)).as("no shared slug")
				.noneMatch(shared -> shared.slug().equals(base));
	}

	@Test
	void givenStoredSlug_whenTheTeamIsRenamedAndAllocatedAgain_thenItsSlugStays() {
		// given
		Team team = team("Test_AB_" + id);
		siteSlugService.allocate();
		team.setShortName("Test_Renamed_" + id);
		teamRepository.save(team);

		// when
		var slugs = siteSlugService.allocate();

		// then
		assertThat(slugs.team(team.getId())).as("slug after the rename").isEqualTo(base);
	}

	@Test
	void givenSharedSlugReserved_whenANewTeamCollides_thenItGetsTheNextSuffixAndJoinsTheList() {
		// given
		Team underscore = team("Test_AB_" + id);
		Team dash = team("Test-AB-" + id);
		siteSlugService.allocate();
		Team dot = team("Test.AB." + id);

		// when
		var slugs = siteSlugService.allocate();

		// then
		assertThat(slugs.team(dot.getId())).as("the reserved slug stays reserved").isEqualTo(base + "-4");
		assertThat(slugs.shared(SiteSlugKind.TEAM)).as("the shared slug lists all three teams")
				.contains(new SharedSlug(SiteSlugKind.TEAM, base,
						List.of(underscore.getId(), dash.getId(), dot.getId())));
	}

	@Test
	void givenDriverWithStoredSlug_whenACollidingDriverIsAdded_thenTheFirstKeepsItsSlugAndTheNewOneGetsTheNextSuffix() {
		// given
		Driver first = driver("Test_AB_" + id);
		siteSlugService.allocate();
		Driver second = driver("Test-AB-" + id);

		// when
		var slugs = siteSlugService.allocate();

		// then
		assertThat(slugs.driver(first.getId())).as("existing URL keeps its owner").isEqualTo(base);
		assertThat(slugs.driver(second.getId())).as("new colliding driver").isEqualTo(base + "-2");
	}

	private Team team(String shortName) {
		Team team = testHelper.createTeam(shortName + " Team", shortName);
		teams.add(team);
		return team;
	}

	private Driver driver(String psnId) {
		Driver driver = testHelper.createDriver(psnId, psnId);
		drivers.add(driver);
		return driver;
	}
}
