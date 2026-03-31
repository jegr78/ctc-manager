package de.ctc.admin.controller;

import de.ctc.TestHelper;
import de.ctc.domain.model.Driver;
import de.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DriverControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DriverRepository driverRepository;
    @Autowired private TestHelper testHelper;

    @Test
    void shouldListDrivers() throws Exception {
        mockMvc.perform(get("/admin/drivers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/drivers"))
                .andExpect(model().attributeExists("drivers"));
    }

    @Test
    void shouldShowNewDriverForm() throws Exception {
        mockMvc.perform(get("/admin/drivers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-form"))
                .andExpect(model().attributeExists("driverForm"));
    }

    @Test
    void shouldCreateDriver() throws Exception {
        mockMvc.perform(post("/admin/drivers/save")
                        .param("psnId", "mockmvc_driver")
                        .param("nickname", "MockMvc Driver")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers"));

        var saved = driverRepository.findByPsnId("mockmvc_driver");
        assertTrue(saved.isPresent());
        assertEquals("MockMvc Driver", saved.get().getNickname());
    }

    @Test
    void shouldShowDriverDetail() throws Exception {
        var driver = driverRepository.save(new Driver("detail_test_psn", "Detail Tester"));

        mockMvc.perform(get("/admin/drivers/" + driver.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-detail"))
                .andExpect(model().attributeExists("driver"));
    }

    @Test
    void shouldShowEditForm() throws Exception {
        var driver = driverRepository.save(new Driver("edit_test_psn", "Edit Tester"));

        mockMvc.perform(get("/admin/drivers/" + driver.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-form"))
                .andExpect(model().attributeExists("driverForm", "seasonDrivers", "seasons", "teams"));
    }

    @Test
    void shouldSaveExistingDriver() throws Exception {
        var driver = driverRepository.save(new Driver("update_test_psn", "Original Name"));

        mockMvc.perform(post("/admin/drivers/save")
                        .param("id", driver.getId().toString())
                        .param("psnId", "update_test_psn")
                        .param("nickname", "Updated Name")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers"));

        var updated = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getNickname());
    }

    @Test
    void shouldAssignDriverToSeason() throws Exception {
        var season = testHelper.createSeason("Assign Driver Season");
        var team = testHelper.createTeam("Assign Team", "ASG");
        var driver = driverRepository.save(new Driver("assign_test_psn", "Assign Driver"));

        mockMvc.perform(post("/admin/drivers/" + driver.getId() + "/assign")
                        .param("seasonId", season.getId().toString())
                        .param("teamId", team.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/" + driver.getId() + "/edit"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldDeleteDriver() throws Exception {
        var driver = driverRepository.save(new Driver("delete_driver", "Delete Driver"));

        mockMvc.perform(post("/admin/drivers/" + driver.getId() + "/delete"))
                .andExpect(status().is3xxRedirection());

        assertFalse(driverRepository.findById(driver.getId()).isPresent());
    }
}
