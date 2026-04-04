package org.ctc.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AccessDeniedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenAccessDenied_thenReturnsAccessDeniedView() throws Exception {
        // when / then
        mockMvc.perform(get("/admin/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/access-denied"))
                .andExpect(model().attribute("status", 403))
                .andExpect(model().attribute("error", "Access Denied"))
                .andExpect(model().attribute("message", "You do not have permission to access this resource."));
    }
}
