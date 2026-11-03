package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.IncidentClockConfiguration;
import org.ctc.testsupport.IncidentTestClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Import(IncidentClockConfiguration.class)
@TestPropertySource(properties = "ctc.outbound.enabled=false")
@Tag("integration")
public abstract class IncidentIntegrationFixture {
    protected static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    protected static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    protected final IncidentManagementActor manager = new IncidentManagementActor("test-manager");
    @Autowired protected TestHelper helper;
    @Autowired protected TransactionTemplate tx;
    @Autowired protected EntityManager em;
    @Autowired protected IncidentTestClock clock;
    @Autowired protected SeasonRepository seasons;
    @Autowired protected TeamRepository teams;
    @Autowired protected MatchRepository matches;
    @Autowired protected RaceRepository races;
    @Autowired protected DiscordGlobalConfigRepository configs;
    @Autowired protected IncidentMatchContextRepository contextRows;
    @Autowired protected IncidentReportWindowRepository windowRows;
    @Autowired protected IncidentReportRepository reportRows;
    @Autowired protected IncidentReportRevisionRepository revisionRows;
    @Autowired protected IncidentReportEventRepository eventRows;
    @Autowired protected IncidentContextService contexts;
    @Autowired protected IncidentWindowService windows;
    @Autowired protected IncidentReportService reports;
    @MockitoBean protected IncidentReportAccess access;
    protected TestHelper.SeasonFixture fixture;
    protected final List<TestHelper.SeasonFixture> extras = new ArrayList<>();
    protected UUID contextId;
    protected UUID windowId;
    protected Long configId;
    protected String originalGuild;
    protected boolean createdConfig;
    protected AuthorizedIncidentActor actor;
    protected final IncidentPayload payload = new IncidentPayload("Test driver and unlisted guest", "00:00", "Test <private> incident description", "https://example.invalid/video");

    @BeforeEach
    protected void setUp() {
        clock.set(NOW);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TIC" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.match().setDiscordChannelId(CHANNEL);
            var config = configs.findFirstByOrderByIdAsc(); createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            originalGuild = config.getGuildId(); config.setGuildId(GUILD); configId = configs.saveAndFlush(config).getId();
        });
        contextId = contexts.bind(IncidentMatchRef.match(fixture.match().getId()), GUILD, CHANNEL, manager);
        windowId = tx.execute(status -> windowRows.findByContextId(contextId).orElseThrow().getId());
        actor = new AuthorizedIncidentActor(contextId, GUILD, CHANNEL, "400000000000000004",
                new IncidentParty(IncidentPartyType.TEAM, fixture.homeTeam().getId(), fixture.homeTeam().getName(), "300000000000000003"));
    }

    @AfterEach
    protected void cleanUp() {
        tx.executeWithoutResult(status -> {
            if (fixture != null) deleteFixture(fixture);
            for (var extra : extras) deleteFixture(extra);
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
}
