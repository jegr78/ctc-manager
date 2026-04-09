package org.ctc.admin;

import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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

    /** The 10 fictive parent team short names seeded by TestDataService. */
    private static final Set<String> SEEDED_PARENT_SHORT_NAMES = Set.of(
            "VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");

    /** The 7 fictive sub-team short names seeded by TestDataService. */
    private static final Set<String> SEEDED_SUB_SHORT_NAMES = Set.of(
            "VRX A", "VRX B", "SGM B", "SGM S", "TBR R", "TBR B", "TBR G");

    // -------------------------------------------------------
    // Task 1: Team structure tests
    // -------------------------------------------------------

    @Test
    void givenDevSeed_whenStarted_thenExactlyTenParentTeamsExist() {
        // given — dev profile seed() has already run at startup

        // when — count only known seeded parent teams
        long parentCount = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() == null)
                .filter(t -> SEEDED_PARENT_SHORT_NAMES.contains(t.getShortName()))
                .count();

        // then
        assertThat(parentCount).isEqualTo(10);
    }

    @Test
    void givenDevSeed_whenStarted_thenAtLeastTwoParentsHaveTwoOrMoreSubTeams() {
        // when — only count seeded sub-teams
        var subTeams = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() != null)
                .filter(t -> SEEDED_SUB_SHORT_NAMES.contains(t.getShortName()))
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
    void givenDevSeed_whenStarted_thenTotalNonTestTeamCountIsAtLeastFourteen() {
        // when — count known seeded teams (parents + sub-teams)
        long seededTeamCount = teamRepository.findAll().stream()
                .filter(t -> SEEDED_PARENT_SHORT_NAMES.contains(t.getShortName())
                        || SEEDED_SUB_SHORT_NAMES.contains(t.getShortName()))
                .count();

        // then — 10 parents + 7 sub-teams = 17 total
        assertThat(seededTeamCount).isGreaterThanOrEqualTo(14);
    }

    @Test
    void givenDevSeed_whenStarted_thenNoRealCtcTeamNamesExist() {
        // when
        var allTeamNames = teamRepository.findAll().stream()
                .map(t -> t.getName())
                .toList();

        // then — none of the real CTC team names should appear
        assertThat(allTeamNames).doesNotContain(
                "Project One Racing",
                "Community League Racing",
                "Tidgney Community Racing",
                "Amigos Racing Team",
                "Apex Hunter Racing",
                "Medway Racing League",
                "Gen-X Racing",
                "Dream Team Racing",
                "VEZ Racing Team",
                "The Neutrals Racing"
        );
    }

    @Test
    void givenDevSeed_whenStarted_thenEveryParentTeamHasShortNameOfThreeToFourCharacters() {
        // when — only check seeded parent teams
        var parentTeams = teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() == null)
                .filter(t -> SEEDED_PARENT_SHORT_NAMES.contains(t.getShortName()))
                .toList();

        // then
        for (var team : parentTeams) {
            assertThat(team.getShortName().length())
                    .as("Team %s has shortName '%s' with length %d", team.getName(), team.getShortName(), team.getShortName().length())
                    .isBetween(3, 4);
        }
    }

    @Test
    void givenDevSeed_whenStarted_thenEverySeededTeamHasNonNullColors() {
        // when — only check seeded teams (not teams created by other integration tests)
        var seededTeams = teamRepository.findAll().stream()
                .filter(t -> SEEDED_PARENT_SHORT_NAMES.contains(t.getShortName())
                        || SEEDED_SUB_SHORT_NAMES.contains(t.getShortName()))
                .toList();

        // then
        for (var team : seededTeams) {
            assertThat(team.getPrimaryColor())
                    .as("Team %s should have primaryColor", team.getShortName())
                    .isNotNull();
            assertThat(team.getSecondaryColor())
                    .as("Team %s should have secondaryColor", team.getShortName())
                    .isNotNull();
            assertThat(team.getAccentColor())
                    .as("Team %s should have accentColor", team.getShortName())
                    .isNotNull();
        }
    }

    @Test
    void givenDevSeed_whenStarted_thenE2ETestTeamsStillExist() {
        // when
        var allShortNames = teamRepository.findAll().stream()
                .map(t -> t.getShortName())
                .toList();

        // then
        assertThat(allShortNames).contains("T-ALF", "T-BRV");
    }

    // -------------------------------------------------------
    // Task 2: Driver structure tests
    // -------------------------------------------------------

    @Test
    void givenDevSeed_whenStarted_thenExactlyOneHundredNonTestDriversExist() {
        // given — 10 teams x 10 drivers = 100 non-test drivers
        // Filter by known seeded PSN ID prefixes to avoid counting drivers created by other integration tests

        // when
        long seededDriverCount = driverRepository.findAll().stream()
                .filter(d -> d.getPsnId().matches("(VRX|SGM|ADR|TBR|ICL|SVT|NFR|EGP|HMS|PWR)_Driver\\d+"))
                .count();

        // then
        assertThat(seededDriverCount).isEqualTo(100);
    }

    @Test
    void givenDevSeed_whenStarted_thenNoRealPsnIdsExist() {
        // when
        var allPsnIds = driverRepository.findAll().stream()
                .map(d -> d.getPsnId())
                .toList();

        // then
        assertThat(allPsnIds).doesNotContain(
                "France-k88",
                "BetelgeuzeFIN",
                "Etlits",
                "ART_Lango666",
                "AHR_Hills_93",
                "ApexMagnet",
                "Gen-X_Dan98",
                "DTR_Butzen-Katz",
                "andreahoppus",
                "Chaz__CA"
        );
    }

    @Test
    void givenDevSeed_whenStarted_thenAtLeastTwoDriversHaveAliases() {
        // when
        long driversWithAliases = driverRepository.findAll().stream()
                .filter(d -> !d.getAliases().isEmpty())
                .count();

        // then
        assertThat(driversWithAliases).isGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------
    // Task 3: Team card generation tests
    // -------------------------------------------------------

    @Test
    void givenDevSeed_whenStarted_thenActiveSeasonExists() {
        // given — dev profile seed() has already run at startup
        // TeamCardService.generateAllCards() may throw in test env (no Playwright Chromium)

        // when — seed() was called by DevDataSeeder at startup

        // then — application context loaded successfully (this test running proves it)
        var activeSeason = seasonRepository.findAll().stream()
                .filter(s -> s.isActive())
                .findFirst();
        assertThat(activeSeason).isPresent();
    }
}
