package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.repository.DriverRepository;
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
    void whenGetDrivers_thenReturnsDriversView() throws Exception {
        // when
        mockMvc.perform(get("/admin/drivers"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/drivers"))
                .andExpect(model().attributeExists("drivers"));
    }

    @Test
    void whenGetNewDriverForm_thenReturnsDriverForm() throws Exception {
        // when
        mockMvc.perform(get("/admin/drivers/new"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-form"))
                .andExpect(model().attributeExists("driverForm"));
    }

    @Test
    void givenValidDriverForm_whenSaveDriver_thenRedirectsAndPersists() throws Exception {
        // when
        mockMvc.perform(post("/admin/drivers/save")
                        .param("psnId", "mockmvc_driver")
                        .param("nickname", "MockMvc Driver")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers"));

        // then
        var saved = driverRepository.findByPsnId("mockmvc_driver");
        assertTrue(saved.isPresent());
        assertEquals("MockMvc Driver", saved.get().getNickname());
    }

    @Test
    void givenExistingDriver_whenGetDriverDetail_thenReturnsDetailView() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("detail_test_psn", "Detail Tester"));

        // when
        mockMvc.perform(get("/admin/drivers/" + driver.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-detail"))
                .andExpect(model().attributeExists("driver"));
    }

    @Test
    void givenExistingDriver_whenGetEditForm_thenReturnsDriverForm() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("edit_test_psn", "Edit Tester"));

        // when
        mockMvc.perform(get("/admin/drivers/" + driver.getId() + "/edit"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-form"))
                .andExpect(model().attributeExists("driverForm", "seasonDrivers", "seasons", "teams"));
    }

    @Test
    void givenExistingDriver_whenSaveUpdatedDriver_thenRedirectsAndUpdates() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("update_test_psn", "Original Name"));

        // when
        mockMvc.perform(post("/admin/drivers/save")
                        .param("id", driver.getId().toString())
                        .param("psnId", "update_test_psn")
                        .param("nickname", "Updated Name")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers"));

        // then
        var updated = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getNickname());
    }

    @Test
    void givenDriverAndSeasonAndTeam_whenAssignDriver_thenRedirectsWithSuccess() throws Exception {
        // given
        var season = testHelper.createSeason("Assign Driver Season");
        var team = testHelper.createTeam("Assign Team", "ASG");
        var driver = driverRepository.save(new Driver("assign_test_psn", "Assign Driver"));

        // when
        mockMvc.perform(post("/admin/drivers/" + driver.getId() + "/assign")
                        .param("seasonId", season.getId().toString())
                        .param("teamId", team.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/" + driver.getId() + "/edit"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenDriverWithAliases_whenGetEditForm_thenFormContainsAliases() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("alias_edit_psn", "Alias Tester"));
        driver.addAlias("OldPsn_1");
        driver.addAlias("OldPsn_2");
        driverRepository.save(driver);

        // when
        mockMvc.perform(get("/admin/drivers/" + driver.getId() + "/edit"))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("driverForm"))
                .andExpect(result -> {
                    var form = (org.ctc.admin.dto.DriverForm) result.getModelAndView().getModel().get("driverForm");
                    assertEquals(2, form.getAliases().size());
                    assertTrue(form.getAliases().contains("OldPsn_1"));
                    assertTrue(form.getAliases().contains("OldPsn_2"));
                });
    }

    @Test
    void givenValidAliases_whenSaveDriver_thenAliasesPersisted() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("alias_save_psn", "Alias Save"));

        // when
        mockMvc.perform(post("/admin/drivers/save")
                        .param("id", driver.getId().toString())
                        .param("psnId", "alias_save_psn")
                        .param("nickname", "Alias Save")
                        .param("active", "true")
                        .param("aliases[0]", "PreviousPsn1")
                        .param("aliases[1]", "PreviousPsn2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers"));

        // then
        var updated = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals(2, updated.getAliases().size());
    }

    @Test
    void givenConflictingAlias_whenSaveDriver_thenReturnsFormWithError() throws Exception {
        // given
        driverRepository.save(new Driver("existing_psn", "Existing Driver"));
        var driver = driverRepository.save(new Driver("conflict_test_psn", "Conflict Tester"));

        // when
        mockMvc.perform(post("/admin/drivers/save")
                        .param("id", driver.getId().toString())
                        .param("psnId", "conflict_test_psn")
                        .param("nickname", "Conflict Tester")
                        .param("active", "true")
                        .param("aliases[0]", "existing_psn"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-form"))
                .andExpect(model().attributeHasErrors("driverForm"));
    }

    @Test
    void givenExistingDriver_whenDeleteDriver_thenRedirectsAndRemoves() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("delete_driver", "Delete Driver"));

        // when
        mockMvc.perform(post("/admin/drivers/" + driver.getId() + "/delete"))
                .andExpect(status().is3xxRedirection());

        // then
        assertFalse(driverRepository.findById(driver.getId()).isPresent());
    }
}
