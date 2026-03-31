package de.ctc.gt7sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class Gt7SyncControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private Gt7SyncService syncService;

    @Test
    void shouldShowSyncPage() throws Exception {
        mockMvc.perform(get("/admin/gt7-sync"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/gt7-sync"));
    }

    @Test
    void shouldShowPreviewWithMockedService() throws Exception {
        var preview = new Gt7SyncPreview(
                List.of(new Gt7SyncPreview.CarEntry("gt7-1", "Toyota", "GR Supra", "http://img.test/car.png", Gt7SyncPreview.SyncStatus.NEW)),
                List.of(new Gt7SyncPreview.TrackEntry("t-1", "Fuji Speedway", "JP", Gt7SyncPreview.SyncStatus.NEW))
        );
        when(syncService.fetchAndPreview()).thenReturn(preview);

        mockMvc.perform(post("/admin/gt7-sync/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/gt7-sync-preview"))
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    void shouldHandlePreviewError() throws Exception {
        when(syncService.fetchAndPreview()).thenThrow(new IOException("Connection refused"));

        mockMvc.perform(post("/admin/gt7-sync/preview"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/gt7-sync"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldExecuteSyncWithSelectedItems() throws Exception {
        var result = new Gt7SyncService.SyncResult(3, 2, List.of(), 5);
        when(syncService.executeSync(anyList(), anyList())).thenReturn(result);

        mockMvc.perform(post("/admin/gt7-sync/execute")
                        .param("selectedCars", "gt7-1", "gt7-2")
                        .param("selectedTracks", "Fuji Speedway"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/gt7-sync"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldExecuteSyncWithNoSelections() throws Exception {
        var result = new Gt7SyncService.SyncResult(0, 0, List.of(), 1);
        when(syncService.executeSync(anyList(), anyList())).thenReturn(result);

        mockMvc.perform(post("/admin/gt7-sync/execute"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/gt7-sync"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldHandleExecuteError() throws Exception {
        when(syncService.executeSync(anyList(), anyList())).thenThrow(new RuntimeException("Scrape failed"));

        mockMvc.perform(post("/admin/gt7-sync/execute")
                        .param("selectedCars", "gt7-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/gt7-sync"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldExecuteSyncWithWarnings() throws Exception {
        var result = new Gt7SyncService.SyncResult(2, 1, List.of("Image download failed"), 3);
        when(syncService.executeSync(anyList(), anyList())).thenReturn(result);

        mockMvc.perform(post("/admin/gt7-sync/execute")
                        .param("selectedCars", "gt7-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/gt7-sync"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
