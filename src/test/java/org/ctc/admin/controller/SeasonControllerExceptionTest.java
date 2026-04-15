package org.ctc.admin.controller;

import org.ctc.domain.service.SeasonManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for SeasonController exception-handling behavior.
 * Uses @MockitoBean SeasonManagementService to verify narrowed catch blocks.
 * Normal controller behavior is covered in SeasonControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SeasonControllerExceptionTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SeasonManagementService seasonManagementService;

	@Test
	void givenIoException_whenUpdateSeasonTeam_thenRedirectsWithError() throws Exception {
		// given
		var seasonId = UUID.randomUUID();
		var seasonTeamId = UUID.randomUUID();
		when(seasonManagementService.updateSeasonTeam(any(), any(), anyString(), anyString(), anyString(), any()))
				.thenThrow(new IOException("logo upload failed"));
		// stub findAll for common attributes (seasons list in form)
		when(seasonManagementService.findAll()).thenReturn(List.of());

		// when
		mockMvc.perform(post("/admin/seasons/" + seasonId + "/update-season-team")
						.param("seasonTeamId", seasonTeamId.toString())
						.param("rating", "1500")
						.param("primaryColor", "#FF0000")
						.param("secondaryColor", "#000000")
						.param("accentColor", "#FFFFFF"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons/" + seasonId))
				.andExpect(flash().attributeExists("errorMessage"));
	}
}
