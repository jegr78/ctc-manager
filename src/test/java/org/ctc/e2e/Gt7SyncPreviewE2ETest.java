package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.ctc.gt7sync.Gt7SyncPreview;
import org.ctc.gt7sync.Gt7SyncPreview.CarEntry;
import org.ctc.gt7sync.Gt7SyncPreview.SyncStatus;
import org.ctc.gt7sync.Gt7SyncPreview.TrackEntry;
import org.ctc.gt7sync.Gt7SyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
class Gt7SyncPreviewE2ETest extends PlaywrightConfig {
    @MockitoBean Gt7SyncService syncService;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenNewEntriesAcrossPages_whenFilterChangeSelectionAndImport_thenAllSelectedEntriesAreSubmitted() throws Exception {
        var cars = new ArrayList<CarEntry>();
        var ids = new ArrayList<String>();
        for (int index = 0; index < 24; index++) {
            var id = "Test-Sync-Car-" + index;
            ids.add(id);
            cars.add(new CarEntry(id, "Test Manufacturer", "Test Car " + index, "", SyncStatus.NEW));
        }
        cars.add(new CarEntry("Test-Existing-Car", "Test Manufacturer", "Test Existing Car", "", SyncStatus.EXISTS));
        var tracks = List.of(new TrackEntry("Test-Track-1", "Test Track One", "Test Country", SyncStatus.NEW),
                new TrackEntry("Test-Track-2", "Test Track Two", "Test Country", SyncStatus.NEW),
                new TrackEntry("Test-Existing-Track", "Test Existing Track", "Test Country", SyncStatus.EXISTS));
        when(syncService.fetchAndPreview()).thenReturn(new Gt7SyncPreview(cars, tracks));
        when(syncService.executeSync(anyList(), anyList(), anyList(), any())).thenReturn(new Gt7SyncService.SyncResult(24, 2, List.of(), 1));
        openPreview();
        var carSection = page.locator("#sync-cars");
        var trackSection = page.locator("#sync-tracks");
        assertThat(page.locator("[data-sync-selection]")).hasText("24 cars and 2 tracks selected");
        assertThat(carSection.locator("[data-list-entry]:visible")).hasCount(20);
        carSection.locator(".car-check:visible").first().uncheck();
        assertThat(page.locator("[data-sync-selection]")).hasText("23 cars and 2 tracks selected");
        carSection.locator("[data-list-next]").click();
        assertThat(carSection.locator("[data-list-entry]:visible")).hasCount(5);
        carSection.locator("[data-list-status]").selectOption("EXISTS");
        assertThat(carSection.locator("[data-list-entry]:visible")).hasCount(1);
        assertThat(trackSection.locator("[data-list-entry]:visible")).hasCount(3);
        carSection.locator("[data-sync-select=selectedCars]").click();
        assertThat(page.locator("[data-sync-selection]")).hasText("24 cars and 2 tracks selected");
        carSection.locator("[data-sync-clear=selectedCars]").click();
        assertThat(page.locator("[data-sync-selection]")).hasText("0 cars and 2 tracks selected");
        trackSection.locator("[data-sync-clear]").click();
        assertThat(page.locator("#importBtn")).isDisabled();
        carSection.locator("[data-sync-select=selectedCars]").click();
        trackSection.locator("[data-sync-select]").click();
        trackSection.locator("[data-list-search]").fill("does not match");
        assertThat(trackSection.locator("[data-list-empty]")).isVisible();
        assertThat(page.locator("[data-sync-selection]")).hasText("24 cars and 2 tracks selected");
        page.locator("#importBtn").click();
        assertThat(page).hasURL(url("/admin/gt7-sync"));
        assertThat(page.locator(".alert-success")).containsText("24 cars and 2 tracks imported");
        ArgumentCaptor<List<String>> carCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> trackCaptor = ArgumentCaptor.captor();
        verify(syncService).executeSync(carCaptor.capture(), trackCaptor.capture(), eq(List.of()), any());
        assertThat(carCaptor.getValue()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(trackCaptor.getValue()).containsExactlyInAnyOrder("Test Track One", "Test Track Two");
    }

    @Test
    void givenOnlyExistingEntries_whenFilterForNew_thenNoSelectionOrImportIsAvailable() throws Exception {
        when(syncService.fetchAndPreview()).thenReturn(new Gt7SyncPreview(
                List.of(new CarEntry("Test-Existing-Car", "Test Manufacturer", "Test Existing Car", "", SyncStatus.EXISTS)),
                List.of(new TrackEntry("Test-Existing-Track", "Test Existing Track", "Test Country", SyncStatus.EXISTS))));
        openPreview();
        assertThat(page.locator("[data-sync-selection]")).hasText("0 cars and 0 tracks selected");
        assertThat(page.locator("#importBtn")).isDisabled();
        assertThat(page.locator("input[type='checkbox']")).hasCount(0);
        var cars = page.locator("#sync-cars");
        assertThat(cars.locator("[data-sync-select=selectedCars]")).isDisabled();
        cars.locator("[data-list-status]").selectOption("NEW");
        assertThat(cars.locator("[data-list-empty]")).isVisible();
        assertThat(page.locator("#sync-tracks [data-list-entry]:visible")).hasCount(1);
        cars.locator("[data-list-reset]").focus();
        page.keyboard().press("Enter");
        assertThat(cars.locator("[data-list-search]")).isFocused();
        assertThat(cars.locator("[data-list-entry]:visible")).hasCount(1);
    }


    @Test
    void givenMetadataUpdatesAcrossPages_whenSelectFilterAndImport_thenOnlyExplicitUpdatesAreSubmitted() throws Exception {
        var previewId = java.util.UUID.randomUUID();
        var cars = new ArrayList<CarEntry>();
        for (int index = 0; index < 24; index++) {
            var proposal = new org.ctc.domain.service.CarMetadataService.Proposal(
                    org.ctc.domain.model.CarCategory.GR_3, org.ctc.domain.model.Drivetrain.FR,
                    2020, 2020, "GT7", "GT7", "GTPLUS_SUFFIX", "Official suffix '20", "GTPlus candidate", List.of());
            cars.add(new CarEntry("car" + (1000 + index), "Test Brand", "Test Existing " + index,
                    "", SyncStatus.UPDATE, proposal, java.util.UUID.randomUUID(), true, ""));
        }
        when(syncService.fetchAndPreview()).thenReturn(new Gt7SyncPreview(cars, List.of(), previewId, List.of()));
        when(syncService.executeSync(anyList(), anyList(), anyList(), any()))
                .thenReturn(new Gt7SyncService.SyncResult(0, 0, List.of(), 1, 1));
        openPreview();
        assertThat(page.locator("#importBtn")).isDisabled();
        assertThat(page.locator("[data-sync-metadata-selection]")).hasText("0 car metadata updates selected");
        page.getByLabel("Update metadata for Test Brand Test Existing 0", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true)).check();
        page.locator("#sync-cars [data-list-next]").click();
        page.locator("#carSearch").fill("not present");
        assertThat(page.locator("[data-sync-metadata-selection]")).hasText("1 car metadata updates selected");
        assertThat(page.locator("#importBtn")).isEnabled();
        page.locator("#importBtn").click();
        assertThat(page.locator(".alert-success")).containsText("1 car metadata updates");
        verify(syncService).executeSync(eq(List.of()), eq(List.of()), eq(List.of("car1000")), eq(previewId));
    }

    private void openPreview() {
        page.navigate(url("/admin/gt7-sync"));
        page.locator("#fetchBtn").click();
        assertThat(page.locator("h1")).hasText("GT7 Sync Preview");
    }
}
