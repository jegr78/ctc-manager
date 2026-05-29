package org.ctc.admin.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StaticResourceErrorHandlingIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void givenFaviconIco_whenRequested_thenServedWithOk() throws Exception {
		mockMvc.perform(get("/favicon.ico"))
				.andExpect(status().isOk());
	}

	@Test
	void givenMissingStaticResource_whenRequested_thenNotFoundNotServerError() throws Exception {
		mockMvc.perform(get("/nonexistent-resource-xyz.js"))
				.andExpect(status().isNotFound());
	}
}
