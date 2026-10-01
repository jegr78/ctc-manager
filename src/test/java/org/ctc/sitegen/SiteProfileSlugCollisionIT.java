package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates the team and driver pages of a season whose teams and drivers have names that
 * slugify alike, and checks that every profile gets its own file and every link points to it.
 */
@CtcDevSpringBootContext
@Tag("integration")
class SiteProfileSlugCollisionIT {

	@Autowired private SiteSlugService siteSlugService;
	@Autowired private SiteSlugger siteSlugger;
	@Autowired private StandingsPageGenerator standingsPageGenerator;
	@Autowired private TeamProfilePageGenerator teamProfilePageGenerator;
	@Autowired private DriverProfilePageGenerator driverProfilePageGenerator;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private TestHelper testHelper;
	@Autowired private TransactionTemplate transactionTemplate;
	@Autowired private SiteProperties siteProperties;

	@TempDir
	Path out;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private final String base = "test-ab-" + id;
	private Season season;
	private Path seasonDir;
	private String configuredOutputDir;

	@BeforeEach
	void createSeason() {
		configuredOutputDir = siteProperties.getOutputDir();
		siteProperties.setOutputDir(out.toString());
		season = testHelper.createSeason("Test_Slug_" + id);
		seasonDir = out.resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel()));
	}

	@AfterEach
	void restoreOutputDir() {
		siteProperties.setOutputDir(configuredOutputDir);
	}

	@Test
	void givenTeamsAndDriversThatSharedASlug_whenGenerated_thenEachHasItsOwnPageAndTheSharedUrlListsThem()
			throws IOException {
		// given
		Team underscore = team("Test_AB_" + id);
		Team dash = team("Test-AB-" + id);
		Driver underscoreDriver = driver("Test_AB_" + id, underscore);
		Driver dashDriver = driver("Test-AB-" + id, dash);
		play(underscore, dash);

		// when
		generate();

		// then
		assertThat(Files.readString(seasonDir.resolve("team/" + base + "-2.html"))).as("first team's page")
				.contains("Test_AB_" + id + " Team");
		assertThat(Files.readString(seasonDir.resolve("team/" + base + "-3.html"))).as("second team's page")
				.contains("Test-AB-" + id + " Team");
		assertThat(Files.readString(seasonDir.resolve("team/" + base + ".html"))).as("shared team URL")
				.contains("href=\"" + base + "-2.html\"", "href=\"" + base + "-3.html\"");
		assertThat(Files.readString(seasonDir.resolve("standings.html"))).as("standings links")
				.contains("href=\"team/" + base + "-2.html\"", "href=\"team/" + base + "-3.html\"");
		assertThat(Files.readString(seasonDir.resolve("driver/" + base + "-2.html"))).as("first driver's page")
				.contains(underscoreDriver.getPsnId());
		assertThat(Files.readString(seasonDir.resolve("driver/" + base + "-3.html"))).as("second driver's page")
				.contains(dashDriver.getPsnId());
		assertThat(Files.readString(seasonDir.resolve("driver/" + base + ".html"))).as("shared driver URL")
				.contains("href=\"" + base + "-2.html\"", "href=\"" + base + "-3.html\"");
		assertThat(Files.readString(seasonDir.resolve("team/" + base + "-3.html"))).as("second team's driver link")
				.contains("href=\"../driver/" + base + "-3.html\"");
	}

	@Test
	void givenTeamWithAPublishedUrl_whenACollidingTeamJoins_thenTheUrlKeepsItsOwnerAndTheNewTeamGetsANewOne()
			throws IOException {
		// given
		Team first = team("Test_AB_" + id);
		siteSlugService.allocate();
		Team second = team("Test-AB-" + id);
		play(first, second);

		// when
		generate();

		// then
		assertThat(Files.readString(seasonDir.resolve("team/" + base + ".html"))).as("published URL")
				.contains("Test_AB_" + id + " Team").doesNotContain("Test-AB-" + id + " Team");
		assertThat(Files.readString(seasonDir.resolve("team/" + base + "-2.html"))).as("new team's URL")
				.contains("Test-AB-" + id + " Team");
		assertThat(Files.readString(seasonDir.resolve("standings.html"))).as("standings links")
				.contains("href=\"team/" + base + ".html\"", "href=\"team/" + base + "-2.html\"");
	}

	private void generate() {
		var slugs = siteSlugService.allocate();
		transactionTemplate.executeWithoutResult(status -> {
			var stored = seasonRepository.findById(season.getId()).orElseThrow();
			var ctx = new GenerationContext(out, stored, "", "", false, null, slugs);
			var result = new SiteGeneratorService.GenerationResult();
			try {
				standingsPageGenerator.generate(ctx, result);
				teamProfilePageGenerator.generate(ctx, result);
				driverProfilePageGenerator.generate(ctx, result);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			assertThat(result.getErrors()).as("generation errors").isEmpty();
		});
	}

	private Team team(String shortName) {
		Team team = testHelper.createTeam(shortName + " Team", shortName);
		season.addTeam(team);
		season = seasonRepository.save(season);
		phaseTeamRepository.save(new PhaseTeam(seasonPhaseService.findRegularPhase(season.getId()), team));
		return team;
	}

	private Driver driver(String psnId, Team team) {
		Driver driver = testHelper.createDriver(psnId, psnId);
		testHelper.createSeasonDriver(season, driver, team);
		return driver;
	}

	private void play(Team home, Team away) {
		var matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Slug MD " + id, 1);
		var match = testHelper.createMatch(matchday, home, away);
		match.setHomeScore(30);
		match.setAwayScore(10);
		matchRepository.save(match);
		testHelper.createRace(matchday, match);
	}
}
