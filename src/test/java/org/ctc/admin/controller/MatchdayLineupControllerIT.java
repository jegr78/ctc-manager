package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.LineupStatus;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class MatchdayLineupControllerIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository raceLineups;
    @Autowired LineupSubmissionRepository submissions;
    @Autowired PickSessionEpoch epoch;
    TestHelper.SeasonFixture fixture;
    final List<UUID> driverIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TLC" + UUID.randomUUID().toString().substring(0, 6));
            fixture.race().setDateTime(LocalDateTime.of(2035, 2, 1, 20, 0));
            for (int i = 0; i < 6; i++) {
                var driver = helper.createDriver("TLC_D" + i + fixture.homeTeam().getShortName(), "TLC driver " + i);
                driverIds.add(driver.getId());
                helper.createSeasonDriver(fixture.season(), driver, fixture.homeTeam());
            }
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            raceLineups.deleteAll(raceLineups.findByRaceId(fixture.race().getId()));
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            drivers.deleteAllById(driverIds);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenUnsubmittedLeg_whenOpeningOverview_thenListBothTeamsAsMissing() throws Exception {
        // when / then
        mvc.perform(get("/admin/matchday-lineups").param("seasonId", fixture.season().getId().toString()))
                .andExpect(status().isOk()).andExpect(view().name("admin/matchday-lineups"))
                .andExpect(content().string(containsString(fixture.homeTeam().getShortName() + " vs " + fixture.awayTeam().getShortName() + " (home)")))
                .andExpect(content().string(containsString(fixture.awayTeam().getShortName() + " vs " + fixture.homeTeam().getShortName() + " (away)")))
                .andExpect(content().string(containsString("Opens when the settings for this leg are published")));
    }

    @Test
    void givenManagementCorrection_whenApplying_thenWriteTeamRowsAndShowAppliedRevision() throws Exception {
        // given
        String detail = "/admin/matchday-lineups/" + fixture.race().getId() + "/" + fixture.homeTeam().getId();
        var correction = post(detail + "/correction").param("expectedVersion", "0").param("expectedGeneration", epoch.managementGeneration())
                .param("requestId", UUID.randomUUID().toString()).param("reason", "Lineup sent by e-mail");
        for (int i = 0; i < driverIds.size(); i++) correction.param("drivers[" + i + "]", driverIds.get(i).toString());
        mvc.perform(correction).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(detail));
        // when
        mvc.perform(post(detail + "/apply").param("expectedVersion", "1").param("revisionNumber", "1")
                        .param("expectedGeneration", epoch.managementGeneration()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "Lineup applied to the race lineup. Publication remains separate."));
        // then
        assertThat(raceLineups.findByRaceId(fixture.race().getId())).hasSize(6);
        assertThat(submissions.findByRaceIdAndTeamId(fixture.race().getId(), fixture.homeTeam().getId()).orElseThrow().status())
                .isEqualTo(LineupStatus.APPLIED);
        mvc.perform(get(detail)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Revision 1 (applied)")))
                .andExpect(content().string(containsString("Reason: Lineup sent by e-mail")));
    }

    @Test
    void givenStaleVersion_whenApplying_thenRenderTheErrorWithoutWriting() throws Exception {
        // given
        String detail = "/admin/matchday-lineups/" + fixture.race().getId() + "/" + fixture.homeTeam().getId();
        var correction = post(detail + "/correction").param("expectedVersion", "0").param("expectedGeneration", epoch.managementGeneration())
                .param("requestId", UUID.randomUUID().toString()).param("reason", "Phone call").param("drivers[0]", driverIds.getFirst().toString());
        mvc.perform(correction).andExpect(status().is3xxRedirection());
        // when / then
        mvc.perform(post(detail + "/apply").param("expectedVersion", "0").param("revisionNumber", "1").param("expectedGeneration", epoch.managementGeneration()))
                .andExpect(status().isOk()).andExpect(view().name("admin/matchday-lineup-detail"))
                .andExpect(content().string(containsString("The lineup submission changed; reload its current version")));
        assertThat(raceLineups.findByRaceId(fixture.race().getId())).isEmpty();
    }

    @Test
    void givenWindowDecision_whenOpeningLeg_thenRecordReasonAndOpenTheTeamWindow() throws Exception {
        // given
        String detail = "/admin/matchday-lineups/" + fixture.race().getId() + "/" + fixture.awayTeam().getId();
        // when
        mvc.perform(post(detail + "/window").param("kind", "OPEN").param("expectedSequence", "0")
                        .param("expectedGeneration", epoch.managementGeneration()).param("reason", "Settings posted manually"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage", "Lineups opened for this leg until its deadline."));
        // then
        mvc.perform(get(detail)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Opened by development-manager: Settings posted manually")))
                .andExpect(content().string(containsString("Open until the deadline")));
        mvc.perform(post(detail + "/window").param("kind", "CLOSE").param("expectedSequence", "0")
                        .param("expectedGeneration", epoch.managementGeneration()).param("reason", "Stale form"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("The lineup window changed; reload before deciding")));
    }
}
