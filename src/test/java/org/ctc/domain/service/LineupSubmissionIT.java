package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class LineupSubmissionIT {
    static final String GUILD = "100000000000000011", CHANNEL = "200000000000000012", USER = "12345";
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired SeasonDriverRepository seasonDrivers;
    @Autowired RaceRepository races;
    @Autowired RaceLineupRepository raceLineups;
    @Autowired LineupSubmissionRepository submissions;
    @Autowired LineupSubmissionRevisionRepository revisions;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired DiscordGlobalConfigRepository configs;
    @Autowired PickAccessService access;
    @Autowired LineupTargetService targets;
    @Autowired LineupSubmissionService lineupSubmissions;
    @Autowired LineupApplyService applies;
    @Autowired RaceLineupService editor;
    @MockitoBean PickChannelPermissionSource permissions;

    final ManagementPickActor manager = new ManagementPickActor("lineup-manager");
    final List<UUID> driverIds = new ArrayList<>();
    final List<UUID> teamIds = new ArrayList<>();
    UUID seasonId, parent, first, second, opponent, otherOpponent, firstRace, secondRace;
    Long configId;
    String originalGuild;
    boolean createdConfig;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 6);
            var season = helper.createSeason("Test_Lineups_" + suffix);
            var org = helper.createTeam("Test lineup org " + suffix, "TLO" + suffix);
            var sub1 = helper.createSubTeam("Test lineup one " + suffix, "TL1" + suffix, org);
            var sub2 = helper.createSubTeam("Test lineup two " + suffix, "TL2" + suffix, org);
            var x = helper.createTeam("Test lineup x " + suffix, "TLX" + suffix);
            var y = helper.createTeam("Test lineup y " + suffix, "TLY" + suffix);
            season.addTeam(sub1); season.addTeam(sub2); season.addTeam(x); season.addTeam(y);
            season = seasons.save(season);
            var day = helper.createMatchday(season, "Test lineup MD " + suffix, 1);
            var race1 = helper.createRace(day, helper.createMatch(day, sub1, x));
            race1.setDateTime(LocalDateTime.of(2035, 1, 10, 20, 0));
            var race2 = helper.createRace(day, helper.createMatch(day, y, sub2));
            race2.setDateTime(LocalDateTime.of(2035, 1, 10, 21, 0));
            for (int i = 1; i <= 7; i++) {
                var driver = helper.createDriver("TLD" + i + suffix, "TLD" + i + suffix);
                driverIds.add(driver.getId());
                helper.createSeasonDriver(season, driver, i <= 6 ? sub1 : sub2);
            }
            var foreign = helper.createDriver("TLF" + suffix, "TLF" + suffix);
            driverIds.add(foreign.getId());
            helper.createSeasonDriver(season, foreign, x);
            var config = configs.findFirstByOrderByIdAsc();
            createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            originalGuild = config.getGuildId();
            config.setGuildId(GUILD);
            configId = configs.saveAndFlush(config).getId();
            bindings.saveAndFlush(new TeamPickChannelBinding(season, org, GUILD, CHANNEL));
            seasonId = season.getId(); parent = org.getId(); first = sub1.getId(); second = sub2.getId();
            opponent = x.getId(); otherOpponent = y.getId(); firstRace = race1.getId(); secondRace = race2.getId();
            teamIds.addAll(List.of(sub1.getId(), sub2.getId(), x.getId(), y.getId(), org.getId()));
        });
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            raceLineups.deleteAll(raceLineups.findByRaceMatchdaySeasonId(seasonId));
            raceLineups.flush();
            var created = seasonDrivers.findBySeasonId(seasonId).stream().map(row -> row.getDriver().getId()).toList();
            helper.deleteSeasonCascade(seasons.findById(seasonId).orElseThrow());
            seasons.flush();
            drivers.deleteAllById(created);
            drivers.deleteAllById(driverIds.stream().filter(id -> !created.contains(id)).toList());
            teams.deleteAllById(teamIds);
            if (createdConfig) configs.deleteById(configId);
            else configs.findById(configId).orElseThrow().setGuildId(originalGuild);
        });
    }

    @Test
    void givenOrganisationChannel_whenListingTargets_thenOfferEverySubTeamLegWithTeamOpponentAndLeg() {
        // given
        open(firstRace); open(secondRace);
        // when
        var rows = targets.list(channel());
        // then
        assertThat(rows).extracting(row -> row.teamId()).containsExactly(first, second);
        assertThat(rows.getFirst().label()).as("the shared channel names the fielding team and opponent")
                .contains(shortName(first) + " vs " + shortName(opponent) + " (home)").contains("2035-01-10 20:00 Berlin");
        assertThat(rows.get(1).label()).contains(shortName(second) + " vs " + shortName(otherOpponent) + " (away)");
    }

    @Test
    void givenUnpublishedSettings_whenTeamSubmits_thenReject() {
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), sixDrivers(), false))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Opens when the settings for this leg are published");
        assertThat(submissions.findByRaceIdAndTeamId(firstRace, first)).isEmpty();
    }

    @Test
    void givenOpenLeg_whenSubmittingAndReplaying_thenStoreOneRevision() {
        // given
        open(firstRace);
        var request = UUID.randomUUID();
        // when
        var receipt = submit(firstRace, first, 0, request, sixDrivers(), false);
        var replay = submit(firstRace, first, 0, request, sixDrivers(), false);
        // then
        assertThat(receipt.revisionNumber()).isEqualTo(1);
        assertThat(replay.revisionNumber()).as("a replayed request returns the original receipt").isEqualTo(1);
        assertThat(revisions.findAll().stream().filter(row -> row.getRequestId().equals(request))).hasSize(1);
        assertThat(targets.load(channel(), firstRace, first).target().status()).isEqualTo(LineupStatus.SUBMITTED);
    }

    @Test
    void givenFewerDrivers_whenSubmittingWithoutConfirmation_thenRejectAndAcceptConfirmedShortfall() {
        // given
        open(firstRace);
        var five = sixDrivers().subList(0, 5);
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), five, false))
                .hasMessage("Confirm explicitly that this lineup has fewer than 6 drivers");
        assertThat(submit(firstRace, first, 0, UUID.randomUUID(), five, true).revisionNumber()).isEqualTo(1);
    }

    @Test
    void givenDeadlinePassed_whenTeamSubmits_thenRejectWhileManagementCorrectsWithReason() {
        // given
        open(firstRace);
        tx.executeWithoutResult(status -> races.findById(firstRace).orElseThrow()
                .setDateTime(LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusMinutes(30).withNano(0)));
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), sixDrivers(), false))
                .hasMessage("The lineup deadline has passed");
        var correction = lineupSubmissions.correct(firstRace, first, 0, UUID.randomUUID(), manager, payload(sixDrivers()), "Sent by e-mail after the deadline");
        assertThat(correction.revisionNumber()).isEqualTo(1);
        var stored = tx.execute(status -> revisions.findByRequestId(correction.requestId()).orElseThrow());
        assertThat(stored.getActorKind()).isEqualTo(LineupActorKind.MANAGEMENT);
        assertThat(stored.getReason()).isEqualTo("Sent by e-mail after the deadline");
        assertThat(stored.lineup().driverIds()).containsExactlyElementsOf(sixDrivers().stream().map(LineupPayload.Entry::driverId).toList());
    }

    @Test
    void givenOpponentAndGuestRows_whenApplying_thenReplaceOnlyFieldingTeamRowsAndCreateProposedDriver() {
        // given
        open(firstRace);
        UUID foreign = driverIds.get(7), guest = driverIds.get(6), stale = driverIds.get(5);
        tx.executeWithoutResult(status -> {
            var race = races.findById(firstRace).orElseThrow();
            raceLineups.save(new RaceLineup(race, drivers.findById(foreign).orElseThrow(), teams.findById(opponent).orElseThrow()));
            raceLineups.save(new RaceLineup(race, drivers.findById(stale).orElseThrow(), teams.findById(first).orElseThrow()));
            raceLineups.save(new RaceLineup(race, drivers.findById(guest).orElseThrow(), teams.findById(first).orElseThrow(), true));
        });
        var entries = new ArrayList<>(sixDrivers().subList(0, 5));
        entries.add(LineupPayload.Entry.proposal("Test_Newbie" + driverIds.size(), "Newbie"));
        var receipt = submit(firstRace, first, 0, UUID.randomUUID(), entries, false);
        // when
        applies.apply(submission(firstRace, first).getId(), receipt.version(), 1, Map.of(), false, manager);
        // then
        tx.executeWithoutResult(status -> {
            var rows = raceLineups.findByRaceId(firstRace);
            assertThat(rows).filteredOn(row -> row.getTeam().getId().equals(opponent)).extracting(row -> row.getDriver().getId())
                    .as("the opponent's rows stay").containsExactly(foreign);
            assertThat(rows).filteredOn(RaceLineup::isGuest).extracting(row -> row.getDriver().getId()).as("guests stay").containsExactly(guest);
            var roster = rows.stream().filter(row -> !row.isGuest() && row.getTeam().getId().equals(first)).map(row -> row.getDriver().getPsnId()).toList();
            assertThat(roster).hasSize(6).doesNotContain(drivers.findById(stale).orElseThrow().getPsnId()).contains("Test_Newbie" + driverIds.size());
            var created = drivers.findByPsnIdIgnoreCase("Test_Newbie" + driverIds.size()).orElseThrow();
            assertThat(created.getNickname()).isEqualTo("Newbie");
            assertThat(seasonDrivers.findBySeasonIdAndDriverId(seasonId, created.getId()).orElseThrow().getTeam().getId())
                    .as("a new driver is registered for the fielding team").isEqualTo(first);
            assertThat(submission(firstRace, first).status()).isEqualTo(LineupStatus.APPLIED);
        });
    }

    @Test
    void givenAppliedLineup_whenTeamResubmitsBeforeDeadline_thenKeepRaceLineupUntilAppliedAgain() {
        // given
        open(firstRace);
        var receipt = submit(firstRace, first, 0, UUID.randomUUID(), sixDrivers(), false);
        applies.apply(submission(firstRace, first).getId(), receipt.version(), 1, Map.of(), false, manager);
        var changed = new ArrayList<>(sixDrivers().subList(0, 5));
        // when
        var resubmitted = submit(firstRace, first, receipt.version() + 1, UUID.randomUUID(), changed, true);
        // then
        assertThat(resubmitted.revisionNumber()).isEqualTo(2);
        tx.executeWithoutResult(status -> {
            assertThat(submission(firstRace, first).status()).isEqualTo(LineupStatus.SUBMITTED_AFTER_APPLY);
            assertThat(raceLineups.findByRaceId(firstRace)).as("the race keeps the applied revision").hasSize(6);
        });
    }

    @Test
    void givenSisterTeamFieldsDriver_whenSubmittingOrEditing_thenRejectOverlapWithVisibleError() {
        // given
        open(firstRace);
        UUID shared = driverIds.get(6);
        tx.executeWithoutResult(status -> raceLineups.save(new RaceLineup(races.findById(secondRace).orElseThrow(),
                drivers.findById(shared).orElseThrow(), teams.findById(second).orElseThrow())));
        var entries = new ArrayList<>(sixDrivers().subList(0, 5));
        entries.add(entry(shared));
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), entries, false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("is already in the lineup of " + shortName(second));
        assertThatThrownBy(() -> editor.saveLineup(firstRace, Map.of(shared, first)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("is already in the lineup of " + shortName(second));
        assertThat(editor.saveLineup(firstRace, Map.of(driverIds.getFirst(), first))).as("other drivers still save").isEqualTo(1);
    }

    @Test
    void givenSisterTeamSubmission_whenSubmittingSameDriver_thenRejectOverlap() {
        // given
        open(firstRace); open(secondRace);
        UUID shared = driverIds.get(6);
        submit(secondRace, second, 0, UUID.randomUUID(), List.of(entry(shared)), true);
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), List.of(entry(shared)), true))
                .hasMessageContaining("is already in the lineup of " + shortName(second));
    }

    @Test
    void givenManagementClosedLeg_whenTeamSubmits_thenReject() {
        // given
        open(firstRace);
        lineupSubmissions.decideWindow(firstRace, LineupWindowKind.CLOSE, 1, manager, "Lineups agreed in the match channel");
        // when / then
        assertThatThrownBy(() -> submit(firstRace, first, 0, UUID.randomUUID(), sixDrivers(), false))
                .hasMessage("Management closed lineups for this leg");
        assertThatThrownBy(() -> lineupSubmissions.decideWindow(firstRace, LineupWindowKind.OPEN, 1, manager, "Stale"))
                .as("a stale window version is rejected").hasMessage("The lineup window changed; reload before deciding");
    }

    private org.ctc.domain.lineups.LineupReceipt submit(UUID race, UUID team, long version, UUID request, List<LineupPayload.Entry> entries, boolean shortfall) {
        var actor = access.authorizeLineupTarget(channel(), race, team);
        return lineupSubmissions.submit(race, team, version, request, actor, payload(entries), shortfall);
    }

    private void open(UUID race) {
        lineupSubmissions.decideWindow(race, LineupWindowKind.OPEN, 0, manager, "Settings were posted outside the bot");
    }

    private LineupSubmission submission(UUID race, UUID team) { return submissions.findByRaceIdAndTeamId(race, team).orElseThrow(); }
    private ChannelPickActor channel() { return access.authorizeChannel(GUILD, CHANNEL, USER); }
    private String shortName(UUID team) { return tx.execute(status -> teams.findById(team).orElseThrow().getShortName()); }
    private static LineupPayload payload(List<LineupPayload.Entry> entries) { return new LineupPayload(6, entries); }
    private LineupPayload.Entry entry(UUID driver) { return new LineupPayload.Entry(driver, driver.toString(), null); }
    private List<LineupPayload.Entry> sixDrivers() { return driverIds.subList(0, 6).stream().map(this::entry).toList(); }
}
