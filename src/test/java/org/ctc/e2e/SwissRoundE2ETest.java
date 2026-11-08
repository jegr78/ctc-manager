package org.ctc.e2e;

import java.nio.file.*;
import java.util.*;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotAnimations;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class SwissRoundE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired SeasonPhaseRepository phases;
    @Autowired PhaseTeamRepository roster;
    @Autowired TeamRepository teams;
    UUID seasonId,phaseId;
    final List<UUID> teamIds=new ArrayList<>();
    @BeforeEach void createOwnedFixture() {
        setupPage();
        tx.executeWithoutResult(status -> {
            var season=helper.createSeason("Test Swiss browser "+UUID.randomUUID()); seasonId=season.getId();
            var phase=phases.findBySeasonIdAndPhaseType(seasonId,PhaseType.REGULAR).orElseThrow(); phaseId=phase.getId(); phase.setFormat(SeasonFormat.SWISS); phase.setLegs(2); phase.setTotalRounds(4);
            for(int i=0;i<4;i++) { var team=helper.createTeam("Test Swiss browser team "+i,"Test_SB_"+seasonId.toString().substring(0,8)+i); teamIds.add(team.getId()); season.addTeam(team); roster.save(new PhaseTeam(phase,team)); }
        });
    }
    @AfterEach void removeOwnedFixture() {
        teardownPage(); tx.executeWithoutResult(status -> {
            roster.deleteAll(roster.findByPhaseId(phaseId)); roster.flush(); helper.deleteSeasonCascade(seasons.findById(seasonId).orElseThrow());
            seasons.flush(); teams.deleteAllById(teamIds);
        });
    }
    @Test void givenHistoricalSwissSeason_whenEnteringAndEditingRoundsOnDesktopAndMobile_thenRetainValuesAndShowWarningsAndErrors() throws Exception {
        page.navigate(url("/admin/seasons/"+seasonId+"/swiss"));
        page.getByText("Maintain round pairings",new Page.GetByTextOptions().setExact(true)).click();
        page.getByTestId("swiss-create-round").click();
        choose(0,0,1); choose(1,2,3);
        assertThat(page.locator("[data-swiss-missing]")).hasText("All places are assigned.");
        page.getByTestId("swiss-save-round").click();
        assertThat(page.locator("h1")).hasText("Swiss rounds");
        page.getByTestId("swiss-create-round").click(); choose(0,0,1); choose(1,2,3);
        assertThat(page.locator("[data-swiss-warnings]")).containsText("played before");
        page.getByTestId("swiss-save-round").click();
        assertThat(page.locator(".alert-warning")).containsText("played before");
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/swiss-workspace-desktop.png")).setFullPage(true).setAnimations(ScreenshotAnimations.DISABLED));
        page.setViewportSize(390,844); page.reload();
        org.assertj.core.api.Assertions.assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/swiss-workspace-mobile.png")).setFullPage(true).setAnimations(ScreenshotAnimations.DISABLED));
        page.setViewportSize(1280,900); page.reload();
        page.getByText("Edit pairings",new Page.GetByTextOptions().setExact(true)).first().click();
        assertThat(page.locator("#swiss-home-0")).not().hasValue("");
        String firstHome=page.locator("#swiss-home-0").inputValue();
        page.locator("#swiss-home-1").selectOption(firstHome);
        page.getByTestId("swiss-save-round").click();
        assertThat(page.locator("[role=alert]").first()).containsText("only once");
        assertThat(page.locator("#swiss-home-1")).hasValue(firstHome);
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/swiss-desktop.png")).setFullPage(true).setAnimations(ScreenshotAnimations.DISABLED));
        page.setViewportSize(390,844);
        // Avoid replaying the failed POST on reload; navigate to the native GET editor.
        page.getByText("Reload current values",new Page.GetByTextOptions().setExact(true)).click();
        assertThat(page.locator("#swiss-home-0")).hasValue(firstHome);
        org.assertj.core.api.Assertions.assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/swiss-mobile.png")).setFullPage(true).setAnimations(ScreenshotAnimations.DISABLED));
        page.getByTestId("swiss-save-round").click(); assertThat(page.locator("h1")).hasText("Swiss rounds");
    }
    private void choose(int row,int home,int away) {
        page.locator("#swiss-home-"+row).selectOption(teamIds.get(home).toString());
        page.locator("#swiss-away-"+row).selectOption(teamIds.get(away).toString());
    }
}
