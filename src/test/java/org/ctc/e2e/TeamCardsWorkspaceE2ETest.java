package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
class TeamCardsWorkspaceE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasonRepository;
    @Autowired SeasonTeamRepository seasonTeamRepository;
    @Autowired TeamRepository teamRepository;
    @MockitoBean TeamCardService cardService;
    @Value("${app.upload-dir}") String uploadDir;
    private Season season;
    private final List<Team> teams = new ArrayList<>();
    private final List<SeasonTeam> assignments = new ArrayList<>();
    private Path cardFile;

    @BeforeEach
    void setUp() throws IOException {
        setupPage();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        season = helper.createSeason("Test-Cards " + suffix, 2088, 92);
        for (int i = 0; i < 2; i++) {
            var team = helper.createTeam("Test Card Team " + i + " " + suffix, "T-CARD-" + i + "-" + suffix);
            teams.add(team);
            assignments.add(seasonTeamRepository.save(new SeasonTeam(season, team)));
        }
        when(cardService.cardExists(any(SeasonTeam.class))).thenAnswer(invocation ->
                ((SeasonTeam) invocation.getArgument(0)).getId().equals(assignments.getFirst().getId()));
        when(cardService.getCardPath(any(SeasonTeam.class))).thenAnswer(invocation ->
                "/uploads/team-cards/Test-" + ((SeasonTeam) invocation.getArgument(0)).getId() + ".png");
        cardFile = Path.of(uploadDir, "team-cards", "Test-" + assignments.getFirst().getId() + ".png");
        Files.createDirectories(cardFile.getParent());
        Files.write(cardFile, Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/a9sAAAAASUVORK5CYII="));
    }

    @AfterEach
    void tearDown() throws IOException {
        teardownPage();
        if (cardFile != null) Files.deleteIfExists(cardFile);
        if (season != null) seasonRepository.deleteById(season.getId());
        teams.forEach(team -> teamRepository.deleteById(team.getId()));
    }

    @Test
    void givenAvailableAndMissingCardsWithoutJavaScript_whenChooseSeasonAndDownload_thenOnlyAvailableArtworkIsDownloaded() throws IOException {
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/tools/team-cards"));
            nativePage.locator("#seasonId").selectOption(season.getId().toString());
            nativePage.locator("#teamCardsSeasonForm button[type=submit]").click();
            var available = nativePage.locator("[data-season-team-id='" + assignments.getFirst().getId() + "']");
            var missing = nativePage.locator("[data-season-team-id='" + assignments.getLast().getId() + "']");
            assertThat(available).containsText("PNG available");
            assertThat(missing).containsText("Not generated");
            assertThat(available.locator("img")).hasAttribute("alt", "Team card for " + teams.getFirst().getName());
            assertThat(missing.locator("[data-card-download]")).hasCount(0);
            var png = nativePage.waitForDownload(() -> available.locator("[data-card-download]").click());
            assertEquals(teams.getFirst().getShortName() + "-card.png", png.suggestedFilename());
            var archive = nativePage.waitForDownload(() -> nativePage.locator("[data-cards-zip]").click());
            try (var zip = new ZipInputStream(Files.newInputStream(archive.path()))) {
                assertEquals(teams.getFirst().getShortName() + "-card.png", zip.getNextEntry().getName());
                assertNull(zip.getNextEntry());
            }
        }
    }

    @Test
    void givenSlowGeneration_whenSubmitTwiceThenRetryAfterFailure_thenBusyStatePreventsDuplicatesAndSeasonIsRetained() throws Exception {
        var release = new CountDownLatch(1);
        when(cardService.generateAllCards(any(Season.class))).thenAnswer(invocation -> {
            release.await(30, TimeUnit.SECONDS);
            throw new IOException("Test generation failure");
        }).thenReturn(List.of("/uploads/team-cards/test.png"));
        page.navigate(url("/admin/tools/team-cards?seasonId=" + season.getId()));
        try {
            var state = (Map<?, ?>) page.evaluate("""
                    () => {
                        document.getElementById('generateAllBtn').click();
                        const state = {
                            text: document.getElementById('card-generation-status').textContent,
                            allDisabled: Array.from(document.querySelectorAll('[data-generate-cards] button')).every(button => button.disabled)
                        };
                        document.getElementById('generateAllForm').requestSubmit();
                        return state;
                    }
                    """);
            assertTrue(((String) state.get("text")).startsWith("Generating all cards"));
            assertEquals(true, state.get("allDisabled"));
        } finally {
            release.countDown();
        }
        assertThat(page.locator(".alert-error")).containsText("Test generation failure");
        assertThat(page.locator("#seasonId")).hasValue(season.getId().toString());
        assertThat(page.locator("#generateAllBtn")).isEnabled();
        verify(cardService, times(1)).generateAllCards(any(Season.class));
        page.setViewportSize(390, 768);
        assertEquals(false, page.evaluate("() => document.documentElement.scrollWidth > innerWidth"));
        page.locator("#generateAllBtn").click();
        assertThat(page.locator(".alert-success")).containsText("1 cards generated");
        verify(cardService, times(2)).generateAllCards(any(Season.class));
    }
}
