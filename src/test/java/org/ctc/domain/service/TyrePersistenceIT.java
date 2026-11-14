package org.ctc.domain.service;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_tyre_workflow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class TyrePersistenceIT extends PickPersistenceIT {
    static final Path ROOT = SitegenTestDir.create("tyre-workflow");
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired SeasonLobbyRulesService rules;
    @Autowired SeasonCarPoolService pool;
    @Autowired RaceService nativeRaces;
    final ManagementPickActor manager = new ManagementPickActor("test-tyre-manager");

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.LEGACY, LobbyRulesProfile.CTC_2027);
        pool.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
        UUID entryId = pool.saveOneMakeEntry(fixture.season().getId(), null, "Test explicit tyre One-Make", payload.carId());
        pool.freeze(fixture.season().getId());
        payload = new PickPayload(payload.trackId(), payload.carId(), entryId, payload.laps(), payload.tyreWear(),
                payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(),
                payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(),
                payload.availableTyres(), payload.mandatoryTyres(), LobbyRulesProfile.CTC_2027,
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
    }

    @Test
    void givenExplicitOneMake_whenSubmittingSports_thenPersistRevisionApprovalAndExactCompounds() {
        // given / when / then
        assertThatCode(() -> submitAndApprove(tyres(Set.of(PickTyre.SS, PickTyre.SM, PickTyre.SH), Set.of(PickTyre.SM))))
                .doesNotThrowAnyException();
    }

    @Test
    void givenAllFiveRacingCompounds_whenSubmitting_thenPersistTheFullTwelveCharacterValue() {
        // given / when / then
        assertThatCode(() -> submitAndApprove(tyres(Set.of(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W),
                Set.of(PickTyre.RS, PickTyre.RM, PickTyre.RH)))).doesNotThrowAnyException();
    }

    @Test
    void givenApprovedRacing_whenCorrectingToSports_thenPersistTypedDecisionAndRetainOriginalRevision() {
        // given / when / then
        assertThatCode(() -> correctTo(tyres(Set.of(PickTyre.SS, PickTyre.SM, PickTyre.SH),
                Set.of(PickTyre.SS, PickTyre.SM, PickTyre.SH)))).doesNotThrowAnyException();
    }

    @Test
    void givenApprovedRacing_whenCorrectingToWetAvailability_thenPersistCompleteDecisionValue() {
        // given / when / then
        assertThatCode(() -> correctTo(tyres(Set.of(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W),
                Set.of(PickTyre.RM)))).doesNotThrowAnyException();
    }

    @Test
    void givenApprovedRacing_whenSavingEquivalentCompactNativeText_thenKeepTheExactFingerprintAndOriginalBytes() {
        // given
        submitAndApprove(payload);
        String before = fingerprint();
        // when
        var result = saveNative("RS,RM", "RM");
        // then
        assertThat(result.success()).as(result.message()).isTrue();
        assertThat(fingerprint()).isEqualTo(before);
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId())
                .getSettings().getAvailableTyres()).isEqualTo("RS, RM"));
    }

    @Test
    void givenNewUnclassifiedNativeText_whenSaving_thenRejectBeforePersistingSettings() {
        // given / when / then
        assertThatThrownBy(() -> saveNative("Test unsupported tyre wording", "None"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tyre");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @Test
    void givenExistingUnclassifiedText_whenSavingUnchanged_thenRetainExactBytesWithoutGuessingAFamily() {
        // given
        seedNativeText("Test saved tyre wording", "Test saved requirement wording");
        String before = fingerprint();
        // when
        var result = saveNative("Test saved tyre wording", "Test saved requirement wording");
        // then
        assertThat(result.success()).as(result.message()).isTrue();
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void givenExistingUnclassifiedText_whenReplacingItWithDifferentUnknownText_thenRejectAndRetainTheOriginal() {
        // given
        seedNativeText("Test saved tyre wording", "Test saved requirement wording");
        String before = fingerprint();
        // when / then
        assertThatThrownBy(() -> saveNative("Test different unsupported wording", "Test saved requirement wording"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tyre");
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void givenSavedRacingAliases_whenSubmittingEquivalentCodes_thenRetainAllMediumAndTheExactFingerprint() {
        // given
        seedNativeText("All", "Medium");
        String before = fingerprint();
        // when
        var result = saveNative("RS, RM, RH, I, W", "RM");
        // then
        assertThat(result.success()).as(result.message()).isTrue();
        assertThat(fingerprint()).isEqualTo(before);
        tx.executeWithoutResult(status -> {
            var settings = entityManager.find(Race.class, fixture.race().getId()).getSettings();
            assertThat(settings.getAvailableTyres()).isEqualTo("All");
            assertThat(settings.getMandatoryTyres()).isEqualTo("Medium");
        });
    }

    private RaceService.SaveResult saveNative(String available, String mandatory) {
        return nativeRaces.saveRace(fixture.race().getId(), fixture.matchday().getId(), fixture.homeTeam().getId(),
                fixture.awayTeam().getId(), payload.trackId(), payload.carId(), fixture.race().getDateTime(), payload.laps(),
                payload.tyreWear(), payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel().settingsValue(),
                payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(),
                payload.timeOfDay(), available, mandatory, payload.carPoolEntryId(), payload.lobbyRulesProfile(), payload.startType(), payload.slipstream());
    }

    private void seedNativeText(String available, String mandatory) {
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            race.setCar(cars.findById(payload.carId()).orElseThrow());
            race.setTrack(tracks.findById(payload.trackId()).orElseThrow());
            race.setCarPoolEntry(entityManager.find(SeasonCarPoolEntry.class, payload.carPoolEntryId()));
            var settings = new RaceSettings(race); RaceSettingsValues.from(payload).applyTo(settings);
            settings.setAvailableTyres(available); settings.setMandatoryTyres(mandatory); race.setSettings(settings);
        });
    }

    private String fingerprint() {
        return tx.execute(status -> PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).fingerprint());
    }

    private void submitAndApprove(PickPayload choice) {
        UUID request = UUID.randomUUID();
        var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, choice);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test tyre setup checked");
        tx.executeWithoutResult(status -> {
            assertThat(revisions.findByRequestId(request).orElseThrow().getPayload()).isEqualTo(choice);
            assertThat(revisions.findByRequestId(request).orElseThrow().receipt()).isEqualTo(receipt);
            assertThat(PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).matches(choice)).isTrue();
        });
    }

    private void correctTo(PickPayload choice) {
        UUID request = UUID.randomUUID();
        submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test original Racing setup checked");
        String fingerprint = tx.execute(status -> PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).fingerprint());
        corrections.record(fixture.race().getId(), 2, manager, "Test corrected tyre strategy", choice);
        tx.executeWithoutResult(status -> {
            assertThat(revisions.findByRequestId(request).orElseThrow().getPayload()).isEqualTo(payload);
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(decisions.findAll()).filteredOn(decision -> decision.getPick().getId().equals(pick.getId())
                    && decision.getKind() == PickDecisionKind.CORRECTION).singleElement()
                    .satisfies(decision -> assertThat(decision.getCorrectionPayload()).isEqualTo(choice));
            assertThat(PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).matches(choice)).isTrue();
        });
    }

    private PickPayload tyres(Set<PickTyre> available, Set<PickTyre> mandatory) {
        return new PickPayload(payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(), payload.tyreWear(),
                payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(),
                payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(),
                available, mandatory, payload.lobbyRulesProfile(), payload.startType(), payload.slipstream());
    }
}
