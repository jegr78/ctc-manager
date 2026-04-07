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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
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

    @Test
    void givenExistingDriver_whenGetMergeForm_thenReturnsMergeView() throws Exception {
        // given
        var driver = driverRepository.save(new Driver("merge_source_psn", "Merge Source"));

        // when
        mockMvc.perform(get("/admin/drivers/" + driver.getId() + "/merge"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-merge"))
                .andExpect(model().attributeExists("source", "allDrivers"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenTwoDrivers_whenGetMergeForm_thenSourceExcludedFromDropdown() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_excl_src", "Source"));
        var target = driverRepository.save(new Driver("merge_excl_tgt", "Target"));

        // when
        mockMvc.perform(get("/admin/drivers/" + source.getId() + "/merge"))
                // then
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var allDrivers = (List<Driver>) result.getModelAndView().getModel().get("allDrivers");
                    var ids = allDrivers.stream().map(Driver::getId).toList();
                    assertFalse(ids.contains(source.getId()), "Source driver should be excluded from dropdown");
                    assertTrue(ids.contains(target.getId()), "Target driver should be in dropdown");
                });
    }

    @Test
    void givenTwoDrivers_whenPostPreview_thenReturnsPreviewState() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_prev_src", "Preview Source"));
        var target = driverRepository.save(new Driver("merge_prev_tgt", "Preview Target"));

        // when
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                        .param("targetId", target.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-merge"))
                .andExpect(model().attributeExists("source", "target", "preview"));
    }

    @Test
    void givenTwoDrivers_whenConfirmMerge_thenRedirectsToTarget() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_exec_src", "Exec Source"));
        var target = driverRepository.save(new Driver("merge_exec_tgt", "Exec Target"));

        // when
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge")
                        .param("targetId", target.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/" + target.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenTwoDrivers_whenConfirmMerge_thenSourceDriverDeleted() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_del_src", "Del Source"));
        var target = driverRepository.save(new Driver("merge_del_tgt", "Del Target"));

        // when
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge")
                        .param("targetId", target.getId().toString()));

        // then
        assertFalse(driverRepository.findById(source.getId()).isPresent(), "Source driver should be deleted");
        assertTrue(driverRepository.findById(target.getId()).isPresent(), "Target driver should still exist");
    }

    @Test
    void givenDriver_whenPreviewMergeWithSelf_thenRedirectsToMergeFormWithError() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_self_err", "Self Merge Error"));

        // when
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                        .param("targetId", source.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/" + source.getId() + "/merge"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenDriver_whenPreviewMergeWithNonExistentTarget_thenRedirectsToMergeFormWithError() throws Exception {
        // given
        var source = driverRepository.save(new Driver("merge_missing_tgt", "Missing Target Test"));
        var nonExistentId = UUID.randomUUID();

        // when
        mockMvc.perform(post("/admin/drivers/" + source.getId() + "/merge/preview")
                        .param("targetId", nonExistentId.toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/" + source.getId() + "/merge"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
