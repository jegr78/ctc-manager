package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.gt7sync.environment.GtPlanetEnvironmentSource;
import org.ctc.gt7sync.environment.TrackEnvironmentCatalog;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@Tag("e2e")
class TrackEnvironmentImportE2ETest extends PlaywrightConfig {
    @Autowired TrackRepository tracks;
    @MockitoBean TrackEnvironmentCatalog catalog;
    final List<Track> owned=new ArrayList<>();
    final String prefix="Test environment import "+UUID.randomUUID().toString().substring(0,8);
    @BeforeEach void create() {
        setupPage();
        when(catalog.find(anyString(),any())).thenAnswer(call->{
            String name=call.getArgument(0);
            if(owned.stream().noneMatch(track->track.getName().equals(name)))return null;
            GtPlanetEnvironmentSource.Snapshot source=call.getArgument(1);
            return source.profiles().stream().filter(p->p.key().equals("Europe-Middle East | 24 Heures du Mans Racing Circuit (All Layouts)")).findFirst().orElseThrow();
        });
    }
    @AfterEach void cleanup(){teardownPage();tracks.deleteAll(owned);}
    Track track(int index){var track=tracks.saveAndFlush(new Track(prefix+" "+index,"Test country"));owned.add(track);return track;}
    @Test void givenOfflineDefaults_whenApplyingWithoutJavascriptAndThenEditing_thenKeepNativeControlsAndProtectManualSettings() {
        var track=track(0);
        try(var nativeContext=browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage=nativeContext.newPage();nativePage.navigate(url("/admin/gt7-sync"));nativePage.getByText("Preview layout environments",new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            nativePage.locator("input[name='selectedTracks'][value='"+track.getId()+"']").check();nativePage.getByText("Apply selected defaults",new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            assertThat(nativePage.locator(".alert-success")).containsText("1 layout environments imported");
            nativePage.navigate(url("/admin/tracks/"+track.getId()+"/edit"));assertThat(nativePage.locator("#rainCapability")).hasValue("RAIN");
            nativePage.locator("#timeOfDayText").fill("Night");nativePage.locator("#track-environment button[type='submit']").click();
            assertThat(nativePage.locator("#track-environment")).containsText("Manually maintained");
            nativePage.navigate(url("/admin/gt7-sync/environment"));
            assertThat(nativePage.locator("tr[data-search='"+track.getName()+"']")).containsText("Manual settings protected");
            assertThat(nativePage.locator("input[name='selectedTracks']")).hasCount(0);
        }
    }
    @Test void givenLayoutsAcrossPages_whenFilteringAndSelecting_thenRetainOffPageSelectionsAndFitMobileViewport() {
        for(int index=0;index<25;index++)track(index);
        page.navigate(url("/admin/gt7-sync/environment"));
        assertThat(page.locator("[data-list-entry]:visible")).hasCount(20);
        page.locator("[data-environment-select]").click();assertThat(page.locator("[data-environment-count]")).hasText("25 layout updates selected");
        page.locator("[data-list-search]").fill(prefix+" 24");assertThat(page.locator("[data-list-entry]:visible")).hasCount(1);
        assertThat(page.locator("input[name='selectedTracks']:checked")).hasCount(25);
        page.locator("[data-environment-clear]").click();assertThat(page.locator("[data-environment-count]")).hasText("0 layout updates selected");
        assertThat(page.locator("[data-environment-import] button[type='submit']")).isDisabled();
        page.locator("[data-list-entry]:visible input[name='selectedTracks']").check();
        for(int width:new int[]{1440,390}) {
            page.setViewportSize(width,900);
            if(width<768)page.waitForFunction("document.querySelector('.sidebar').getBoundingClientRect().right <= 0");
            org.junit.jupiter.api.Assertions.assertFalse((Boolean)page.evaluate("document.documentElement.scrollWidth > innerWidth"));
            assertThat(page.locator("[data-environment-count]")).hasText("1 layout updates selected");
        }
        page.locator("[data-environment-import] button[type='submit']").click();assertThat(page.locator(".alert-success")).containsText("1 layout environments imported");
        org.assertj.core.api.Assertions.assertThat(owned.stream().map(t->tracks.findById(t.getId()).orElseThrow()).filter(t->t.getEnvironmentVersion()==1).count()).isEqualTo(1);
    }
}
