package de.ctc.gt7sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class Gt7SyncControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldShowSyncPage() throws Exception {
        mockMvc.perform(get("/admin/gt7-sync"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/gt7-sync"));
    }
}
