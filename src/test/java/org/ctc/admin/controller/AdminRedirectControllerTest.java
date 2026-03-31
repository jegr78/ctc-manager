package org.ctc.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenGetRoot_thenRedirectsToSeasons() throws Exception {
        // when
        mockMvc.perform(get("/"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));
    }

    @Test
    void whenGetAdmin_thenRedirectsToSeasons() throws Exception {
        // when
        mockMvc.perform(get("/admin"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));
    }
}
