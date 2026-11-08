package org.ctc.discord.incidents;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.ctc.TestHelper;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.picks.PickGatewayDispatcher;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.IncidentClockConfiguration;
import org.ctc.testsupport.IncidentTestClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@Import(IncidentClockConfiguration.class)
@TestPropertySource(properties = "ctc.outbound.enabled=false")
@Tag("integration")
abstract class IncidentIntakeIntegrationFixture {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    static final String USER = "400000000000000004", OTHER = "400000000000000005";
    static final String HOME_ROLE = "300000000000000003", AWAY_ROLE = "300000000000000004";
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final String PRIVATE = "Test private case marker 289";
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;
    @Autowired IncidentTestClock clock;
    @Autowired PickGatewayDispatcher dispatcher;
    @Autowired ImportLockService imports;
    @Autowired PickSessionEpoch epoch;
    @Autowired IncidentAccessService access;
    @Autowired IncidentContextService contexts;
    @Autowired IncidentWindowService windows;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired MatchRepository matches;
    @Autowired DiscordGlobalConfigRepository configs;
    @Autowired IncidentReportWindowRepository windowRows;
    @Autowired IncidentReportRepository reportRows;
    @Autowired IncidentReportRevisionRepository revisionRows;
    @Autowired IncidentReportEventRepository eventRows;
    @MockitoBean IncidentChannelAccessSource source;
    final Map<String, Set<String>> roles = new ConcurrentHashMap<>();
    final Set<String> inaccessible = ConcurrentHashMap.newKeySet();
    final List<TestHelper.SeasonFixture> extras = new ArrayList<>();
    final List<UUID> parents = new ArrayList<>();
    final List<UUID> ownedDrivers = new ArrayList<>();
    TestHelper.SeasonFixture fixture;
    UUID contextId, windowId;
    Long configId;
    String originalGuild;
    boolean createdConfig;
    final IncidentManagementActor manager = new IncidentManagementActor("test-manager");

    @BeforeEach
    void setUpIntake() {
        clock.set(NOW); roles.put(USER, Set.of(HOME_ROLE)); roles.put(OTHER, Set.of(HOME_ROLE));
        when(source.currentRoles(eq(GUILD), anyString(), anyString())).thenAnswer(call -> inaccessible.contains(call.getArgument(1)) ? null : roles.get(call.getArgument(2)));
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TIR" + UUID.randomUUID().toString().substring(0, 7));
            fixture.homeTeam().setDiscordRoleId(HOME_ROLE); fixture.awayTeam().setDiscordRoleId(AWAY_ROLE);
            lineup(fixture.race());
            fixture.race().setDateTime(plannedRaceStart()); fixture.match().setDiscordChannelId(CHANNEL);
            var config = configs.findFirstByOrderByIdAsc(); createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            originalGuild = config.getGuildId(); config.setGuildId(GUILD); configId = configs.saveAndFlush(config).getId();
        });
        contextId = contexts.bind(IncidentMatchRef.match(fixture.match().getId()), GUILD, CHANNEL, manager);
        windows.open(contextId, openingDeadline(), 0, manager);
        windowId = tx.execute(status -> windowRows.findByContextId(contextId).orElseThrow().getId());
        dispatcher.start();
    }

    LocalDateTime plannedRaceStart() { return LocalDateTime.of(2035, 1, 1, 20, 0); }
    Instant openingDeadline() { return NOW.plusSeconds(60); }

    @AfterEach
    void cleanUpIntake() {
        dispatcher.stop();
        tx.executeWithoutResult(status -> {
            if (fixture != null) deleteFixture(fixture);
            for (var extra : extras) deleteFixture(extra);
            for (UUID parent : parents) teams.deleteById(parent);
            drivers.deleteAllById(ownedDrivers);
            if (configId != null) {
                if (createdConfig) configs.deleteById(configId);
                else configs.findById(configId).orElseThrow().setGuildId(originalGuild);
            }
        });
    }
    private void deleteFixture(TestHelper.SeasonFixture data) {
        helper.deleteSeasonCascade(seasons.findById(data.season().getId()).orElseThrow()); seasons.flush();
        teams.deleteAllById(List.of(data.homeTeam().getId(), data.awayTeam().getId())); teams.flush();
    }
    void lineup(Race race) {
        for (var team : List.of(race.getHomeTeam(), race.getAwayTeam())) {
            var driver = helper.createDriver("Test lineup guest " + UUID.randomUUID().toString().substring(0, 8), "Test incident guest");
            ownedDrivers.add(driver.getId());
            lineups.saveAndFlush(new RaceLineup(race, driver, team, true));
        }
    }
    IncidentGatewayTestDriver flow() { return flow(USER, CHANNEL); }
    IncidentGatewayTestDriver flow(String user, String channel) { return new IncidentGatewayTestDriver(dispatcher, clock, GUILD, channel, user); }
    UUID submit() { flow().draft(fixture.race().getId(), PRIVATE).click("Confirm report"); return rows().getFirst().getId(); }
    List<IncidentReport> rows() { return tx.execute(status -> List.copyOf(reportRows.findByWindowIdOrderByCaseReferenceAsc(windowId))); }
    long firstRequests() { return tx.execute(status -> eventRows.findByWindowIdOrderByAcceptedAtAscIdAsc(windowId).stream().filter(event -> event.getKind() == IncidentEventKind.SUBMITTED).count()); }
    Team parent() {
        return tx.execute(status -> { var parent = teams.saveAndFlush(new Team("Test reporting organization", "T-ORG")); parent.setDiscordRoleId(HOME_ROLE); parents.add(parent.getId()); return parent; });
    }
    String latestDescription(UUID report) { return tx.execute(status -> revisionRows.findByReportIdOrderByRevisionNumberAsc(report).getLast().getPayload().getDescription()); }
}
