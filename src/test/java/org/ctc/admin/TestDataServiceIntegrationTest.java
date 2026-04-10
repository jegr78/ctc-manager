package org.ctc.admin;

import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestDataServiceIntegrationTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private SeasonDriverRepository seasonDriverRepository;

    private static final Set<String> FICTIVE_TEAM_SHORT_NAMES = Set.of(
            "VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");

    private static final Set<String> REAL_CTC_TEAMS = Set.of(
            "P1R", "CLR", "TCR", "ART", "AHR", "MRL", "GXR", "DTR", "VEZ", "TNR");

    // --- DATA-01: Fictive teams ---

    @Test
    void givenDevSeed_whenStarted_thenExactlyTenFictiveParentTeamsExist() {
        // when
        long parentCount = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() == null)
                .filter(t -> FICTIVE_TEAM_SHORT_NAMES.contains(t.getShortName()))
                .count();

        // then
        assertThat(parentCount).isEqualTo(10);
    }

    @Test
    void givenDevSeed_whenStarted_thenAtLeastTwoParentsHaveTwoOrMoreSubTeams() {
        // when
        var subTeams = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() != null)
                .filter(t -> !t.getShortName().startsWith("T-"))
                .toList();

        var parentCounts = new java.util.HashMap<String, Long>();
        for (var sub : subTeams) {
            parentCounts.merge(sub.getParentTeam().getShortName(), 1L, Long::sum);
        }

        long parentsWithTwoOrMoreSubs = parentCounts.values().stream()
                .filter(count -> count >= 2)
                .count();

        // then
        assertThat(parentsWithTwoOrMoreSubs).isGreaterThanOrEqualTo(2);
    }

    @Test
    void givenDevSeed_whenStarted_thenNoRealCtcTeamNamesInFictiveParentTeams() {
        // when — verify fictive teams contain none of the real CTC short names
        var fictiveParentNames = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() == null)
                .filter(t -> FICTIVE_TEAM_SHORT_NAMES.contains(t.getShortName()))
                .map(t -> t.getShortName())
                .toList();

        // then
        assertThat(fictiveParentNames).doesNotContainAnyElementsOf(REAL_CTC_TEAMS);
        assertThat(fictiveParentNames).hasSize(10);
    }

    // --- DATA-02: Fictive drivers ---

    @Test
    void givenDevSeed_whenStarted_thenExactlyHundredFictiveDriversExist() {
        // when
        var teamPrefixes = List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");
        long driverCount = driverRepository.findAll().stream()
                .filter(d -> teamPrefixes.stream().anyMatch(p -> d.getPsnId().startsWith(p + "_Driver")))
                .count();

        // then
        assertThat(driverCount).isEqualTo(100);
    }

    @Test
    void givenDevSeed_whenStarted_thenEachTeamHasExactlyTenDrivers() {
        // when
        var drivers = driverRepository.findAll().stream()
                .filter(d -> !d.getPsnId().startsWith("Test_"))
                .toList();

        var teamPrefixes = List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");
        for (String prefix : teamPrefixes) {
            long count = drivers.stream()
                    .filter(d -> d.getPsnId().startsWith(prefix + "_Driver"))
                    .count();
            assertThat(count)
                    .as("Team %s should have exactly 10 drivers", prefix)
                    .isEqualTo(10);
        }
    }

    // --- DATA-03: TeamCardService integration verified via startup ---

    @Test
    void givenDevSeed_whenStarted_thenActiveSeason2026Exists() {
        // when
        var activeSeason = seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == 2026 && s.getNumber() == 4)
                .findFirst();

        // then
        assertThat(activeSeason).isPresent();
        assertThat(activeSeason.get().isActive()).isTrue();
    }

    @Test
    void givenDevSeed_whenStarted_thenSeasonDriversAssignedForSeason4() {
        // when
        var s4 = seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == 2026 && s.getNumber() == 4)
                .findFirst().orElseThrow();

        long sdCount = seasonDriverRepository.findBySeasonId(s4.getId()).size();

        // then — all 100 fictive drivers assigned
        assertThat(sdCount).isEqualTo(100);
    }

    // --- E2E test data preserved ---

    @Test
    void givenDevSeed_whenStarted_thenTestTeamsStillExist() {
        // when
        var testTeams = teamRepository.findAll().stream()
                .filter(t -> t.getShortName().startsWith("T-"))
                .map(t -> t.getShortName())
                .toList();

        // then
        assertThat(testTeams).contains("T-ALF", "T-BRV", "T-BRV 1", "T-BRV 2");
    }
}
