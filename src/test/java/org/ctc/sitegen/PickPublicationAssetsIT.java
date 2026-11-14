package org.ctc.sitegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.domain.service.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickPublicationAssetsIT {
    static final Path ROOT = SitegenTestDir.create("private-pick-assets");
    static final String PRIVATE = "TEST-PRIVATE-CAR-TRACK-WEATHER-CACHE-f193";
    @DynamicPropertySource static void directories(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired SiteGeneratorService generator;
    @Autowired RaceRepository races;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired org.ctc.HistoricalPickCorrections corrections;
    @Autowired PickPublicationService publications;
    @MockitoBean YouTubeScraperService youtube;
    TestHelper.SeasonFixture fixture;
    UUID privateCar, privateTrack;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("T-AS" + UUID.randomUUID().toString().substring(0, 6));
            phaseTeams.saveAll(List.of(new PhaseTeam(fixture.matchday().getPhase(), fixture.homeTeam()),
                    new PhaseTeam(fixture.matchday().getPhase(), fixture.awayTeam())));
            var previous = helper.createMatchday(fixture.season(), "T-AS previous round", 0);
            helper.createMatch(previous, fixture.homeTeam(), fixture.awayTeam()).setWalkoverTeam(fixture.homeTeam());
        });
    }
    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            phaseTeams.deleteAll(phaseTeams.findByPhaseId(fixture.matchday().getPhase().getId())); phaseTeams.flush();
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            if (privateCar != null) cars.deleteById(privateCar);
            if (privateTrack != null) tracks.deleteById(privateTrack);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @org.junit.jupiter.api.Test
    void givenSubmittedApprovedAndPrivatelyCorrectedChoices_whenExportingWithCollectionAndGatewayOff_thenWithholdUntilTheExactExplicitOutputSucceeds() throws Exception {
        // given
        PickPayload payload = tx.execute(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow(); var season = race.getMatchday().getSeason();
            var car = cars.save(new Car("Test_Private_Car_" + UUID.randomUUID(), "Unpublished")); privateCar = car.getId();
            var track = tracks.save(new Track("Test_Private_Track_" + UUID.randomUUID())); privateTrack = track.getId();
            season.getCars().add(car); season.getTracks().add(track);
            race.getMatchday().setPicksOpen(true);
            race.setDateTime(java.time.LocalDateTime.of(2035, 1, 1, 20, 0)); race.getMatchday().setPickDeadline(java.time.LocalDateTime.of(2035, 1, 1, 12, 0));
            return new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                    TimeProgressionMode.MULTIPLIER, 0, "Test_Private_Weather", "Sunset", java.util.Set.of(PickTyre.RS), java.util.Set.of());
        });
        String carName = tx.execute(status -> cars.findById(privateCar).orElseThrow().getDisplayName());
        String trackName = tx.execute(status -> tracks.findById(privateTrack).orElseThrow().getName());
        // when / then
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), new AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(), fixture.homeTeam().getId(), "12345"), payload);
        assertSiteWithholds(carName, trackName, payload.weather());
        reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Test approved without publication");
        assertSiteWithholds(carName, trackName, payload.weather());
        var correction = new PickPayload(payload.trackId(), payload.carId(), null, payload.laps(), payload.tyreWear(), payload.fuelConsumption(),
                payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(),
                "Test_Private_Weather_Correction", payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres());
        corrections.record(fixture.race().getId(), 2, new ManagementPickActor("test-admin"), "Test internal correction before first output", correction);
        assertSiteWithholds(carName, trackName, payload.weather(), correction.weather());
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.LOBBY_SETTINGS, new ManagementPickActor("test-admin"), rows ->
                new PreparedPickPublication<>("123456789012345678", "234567890123456789", null, "a".repeat(64), "{}",
                        java.util.Map.of(fixture.race().getId(), PickDisclosureField.lobby()), () -> "test-confirmed-lobby", message -> message));
        String html = generateSiteText(); assertThat(html).contains(trackName).doesNotContain(carName, correction.weather());
    }

    private void assertSiteWithholds(String... values) throws Exception { assertThat(generateSiteText()).doesNotContain(values); }
    private String generateSiteText() throws Exception {
        var result = generator.generate(); assertThat(result.getErrors()).isEmpty(); assertThat(result.getPagesGenerated()).isPositive();
        var content = new StringBuilder();
        try (var files = Files.walk(ROOT.resolve("site"))) {
            for (var file : files.filter(Files::isRegularFile).toList()) content.append(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
        return content.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "normalized", "alias"})
    void givenPrivateCachedGraphicReferencedAsALogo_whenGeneratingTheSite_thenNeverCopyItsBytesOrLinkItsPath(String variant) throws Exception {
        // given
        Path uploads = ROOT.resolve("uploads");
        String cache = "races/" + fixture.race().getId() + "/settings.png";
        Files.createDirectories(uploads.resolve(cache).getParent()); Files.writeString(uploads.resolve(cache), PRIVATE);
        String reference = cache;
        if (variant.equals("normalized")) { Files.createDirectories(uploads.resolve("teams")); reference = "teams/../" + cache; }
        if (variant.equals("alias")) {
            Files.createDirectories(uploads.resolve("teams")); reference = "teams/private-cache-" + fixture.race().getId() + ".png";
            Files.createSymbolicLink(uploads.resolve(reference), uploads.resolve(cache));
        }
        String logo = "/uploads/" + reference;
        tx.executeWithoutResult(status -> teams.findById(fixture.homeTeam().getId()).orElseThrow().setLogoUrl(logo));
        // when
        var result = generator.generate();
        // then
        assertThat(result.getErrors()).isEmpty(); assertThat(result.getPagesGenerated()).isPositive();
        try (var files = Files.walk(ROOT.resolve("site"))) {
            var exported = files.filter(Files::isRegularFile).toList();
            assertThat(exported).anyMatch(path -> path.toString().endsWith(".html"));
            boolean teamProfileGenerated = false;
            for (Path file : exported) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (file.getParent().getFileName().toString().equals("team") && file.toString().endsWith(".html")
                        && content.contains(fixture.homeTeam().getName())) teamProfileGenerated = true;
                assertThat(content).as("public artifact %s", file.getFileName())
                        .doesNotContain(PRIVATE, "/uploads/races/", "private-cache-" + fixture.race().getId());
            }
            assertThat(teamProfileGenerated).as("the participating team's real profile must exercise its own asset copier").isTrue();
        }
        assertThat(Files.readString(uploads.resolve(cache))).isEqualTo(PRIVATE);
    }
}
