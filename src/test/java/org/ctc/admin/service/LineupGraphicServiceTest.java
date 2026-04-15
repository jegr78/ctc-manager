package org.ctc.admin.service;

import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineupGraphicServiceTest {

	@TempDir
	Path tempDir;

	private LineupGraphicService createService() {
		return new LineupGraphicService(null, null, null, null, tempDir.toString());
	}

	@Test
	void givenRaceWithNoTeams_whenGenerateLineup_thenThrowsIllegalState() {
		// given
		var service = createService();
		var race = new Race();

		// when / then
		assertThatThrownBy(() -> service.generateLineup(race))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no teams");
	}

	@Test
	void givenEvenLineupWithHomeAndAwayDrivers_whenBuildPairings_thenMatchesByIndex() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var race = new Race();
		var driverA = new Driver();
		driverA.setPsnId("HomeDriver1");
		driverA.setNickname("Nick_H1");
		var driverB = new Driver();
		driverB.setPsnId("HomeDriver2");
		driverB.setNickname("Nick_H2");
		var driverC = new Driver();
		driverC.setPsnId("AwayDriver1");
		driverC.setNickname("Nick_A1");
		var driverD = new Driver();
		driverD.setPsnId("AwayDriver2");
		driverD.setNickname("Nick_A2");

		var lineups = List.of(
				new RaceLineup(race, driverA, homeTeam),
				new RaceLineup(race, driverB, homeTeam),
				new RaceLineup(race, driverC, awayTeam),
				new RaceLineup(race, driverD, awayTeam)
		);

		// when
		var pairings = service.buildPairings(lineups, homeTeam, awayTeam);

		// then
		assertThat(pairings).hasSize(2);
		assertThat(pairings.get(0).homeDriver()).isEqualTo("HomeDriver1");
		assertThat(pairings.get(0).homeNickname()).isEqualTo("Nick_H1");
		assertThat(pairings.get(0).awayDriver()).isEqualTo("AwayDriver1");
		assertThat(pairings.get(0).awayNickname()).isEqualTo("Nick_A1");
		assertThat(pairings.get(1).homeDriver()).isEqualTo("HomeDriver2");
		assertThat(pairings.get(1).homeNickname()).isEqualTo("Nick_H2");
		assertThat(pairings.get(1).awayDriver()).isEqualTo("AwayDriver2");
		assertThat(pairings.get(1).awayNickname()).isEqualTo("Nick_A2");
	}

	@Test
	void givenSubTeamLineup_whenBuildPairings_thenSubTeamDriverMatchedToParent() {
		// given
		var service = createService();
		var parentHome = new Team("Parent Home", "PH");
		parentHome.setId(UUID.randomUUID());
		var subHome = new Team("Sub Home", "PH 1", parentHome);
		subHome.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var race = new Race();
		var driverA = new Driver();
		driverA.setPsnId("SubDriver1");
		driverA.setNickname("Nick_Sub1");
		var driverB = new Driver();
		driverB.setPsnId("AwayDriver1");
		driverB.setNickname("Nick_Away1");

		var lineups = List.of(
				new RaceLineup(race, driverA, subHome),
				new RaceLineup(race, driverB, awayTeam)
		);

		// when
		var pairings = service.buildPairings(lineups, parentHome, awayTeam);

		// then
		assertThat(pairings).hasSize(1);
		assertThat(pairings.get(0).homeDriver()).isEqualTo("SubDriver1");
		assertThat(pairings.get(0).homeNickname()).isEqualTo("Nick_Sub1");
		assertThat(pairings.get(0).awayDriver()).isEqualTo("AwayDriver1");
		assertThat(pairings.get(0).awayNickname()).isEqualTo("Nick_Away1");
	}

	@Test
	void givenUnevenTeamSizes_whenBuildPairings_thenPairsUpToMinimumWithEmptySlots() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var race = new Race();
		var d1 = new Driver();
		d1.setPsnId("H1");
		d1.setNickname("Nick_H1");
		var d2 = new Driver();
		d2.setPsnId("H2");
		d2.setNickname("Nick_H2");
		var d3 = new Driver();
		d3.setPsnId("H3");
		d3.setNickname("Nick_H3");
		var d4 = new Driver();
		d4.setPsnId("A1");
		d4.setNickname("Nick_A1");
		var d5 = new Driver();
		d5.setPsnId("A2");
		d5.setNickname("Nick_A2");

		var lineups = List.of(
				new RaceLineup(race, d1, homeTeam),
				new RaceLineup(race, d2, homeTeam),
				new RaceLineup(race, d3, homeTeam),
				new RaceLineup(race, d4, awayTeam),
				new RaceLineup(race, d5, awayTeam)
		);

		// when
		var pairings = service.buildPairings(lineups, homeTeam, awayTeam);

		// then
		assertThat(pairings).hasSize(3);
		assertThat(pairings.get(2).homeDriver()).isEqualTo("H3");
		assertThat(pairings.get(2).homeNickname()).isEqualTo("Nick_H3");
		assertThat(pairings.get(2).awayDriver()).isEmpty();
		assertThat(pairings.get(2).awayNickname()).isEmpty();
	}

	@Test
	void givenDriverWithoutNickname_whenBuildPairings_thenFallsBackToPsnId() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var race = new Race();
		var driverA = new Driver();
		driverA.setPsnId("HomeDriver1"); // no nickname set
		var driverB = new Driver();
		driverB.setPsnId("AwayDriver1");
		driverB.setNickname("");

		var lineups = List.of(
				new RaceLineup(race, driverA, homeTeam),
				new RaceLineup(race, driverB, awayTeam)
		);

		// when
		var pairings = service.buildPairings(lineups, homeTeam, awayTeam);

		// then
		assertThat(pairings).hasSize(1);
		assertThat(pairings.get(0).homeNickname()).isEqualTo("HomeDriver1");
		assertThat(pairings.get(0).awayNickname()).isEqualTo("AwayDriver1");
	}

	@Test
	void givenExistingCardFile_whenEncodeCardBase64_thenReturnsDataUri() throws IOException {
		// given
		var service = createService();
		Path cardDir = tempDir.resolve("team-cards").resolve("season1");
		Files.createDirectories(cardDir);
		Path cardFile = cardDir.resolve("TST.png");
		var img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		ImageIO.write(img, "png", cardFile.toFile());

		// when
		String result = service.encodeCardBase64("/uploads/team-cards/season1/TST.png");

		// then
		assertThat(result).startsWith("data:image/png;base64,");
	}

	@Test
	void givenMissingCardFile_whenEncodeCardBase64_thenReturnsNull() {
		// given
		var service = createService();

		// when
		String result = service.encodeCardBase64("/uploads/team-cards/season1/MISSING.png");

		// then
		assertThat(result).isNull();
	}

	@Test
	void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() throws IOException {
		// given
		var service = createService();

		// when / then
		assertThat(service.hasCustomTemplate()).isFalse();
	}

	@Test
	void givenNoCustomTemplate_whenSaveTemplate_thenCustomTemplateExistsAndCanBeLoaded() throws IOException {
		// given
		var service = createService();

		// when
		service.saveTemplate("<html>custom</html>");

		// then
		assertThat(service.hasCustomTemplate()).isTrue();
		assertThat(service.loadTemplate()).isEqualTo("<html>custom</html>");
	}

	@Test
	void givenSavedCustomTemplate_whenResetTemplate_thenNoCustomTemplateExists() throws IOException {
		// given
		var service = createService();
		service.saveTemplate("<html>custom</html>");

		// when
		service.resetTemplate();

		// then
		assertThat(service.hasCustomTemplate()).isFalse();
	}
}
