package org.ctc.backup;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 73-04 — full Spring-context integration test for {@link BackupController}.
 *
 * <p>Boots the {@code dev} profile (H2 in-memory + {@code DevDataSeeder} fixture)
 * with the real {@link org.ctc.backup.service.BackupArchiveService} wired through
 * Jackson MixIns + entity repositories. Two behaviours are pinned end-to-end:
 * <ol>
 *   <li>GET {@code /admin/backup} renders the locked UI-SPEC strings
 *       ({@code Export Backup} CTA and the {@code "all 24 entities"} OQ-1 copy).</li>
 *   <li>POST {@code /admin/backup/export} returns a streamed ZIP whose
 *       first entry is {@code manifest.json} — the wire-contract manifest-first
 *       invariant survives the controller layer.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings() throws Exception {
		// when / then
		mockMvc.perform(get("/admin/backup"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/backup"))
				.andExpect(content().string(Matchers.containsString("Export Backup")))
				.andExpect(content().string(Matchers.containsString("all 24 entities")));
	}

	@Test
	void givenAuthenticatedAdmin_whenPostExport_thenStreamsActualZipWithManifestFirst() throws Exception {
		// when — StreamingResponseBody is processed via Spring async dispatch; the headers
		// land on the initial response but the body is only written once we asyncDispatch.
		MvcResult asyncResult = mockMvc.perform(post("/admin/backup/export"))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						Matchers.matchesPattern("attachment; filename=\"?ctc-backup-\\d{8}T\\d{6}Z\\.zip\"?")))
				.andReturn();

		MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
				.andReturn();

		// then — the streamed body must be a real ZIP whose first entry is manifest.json
		byte[] zipBytes = result.getResponse().getContentAsByteArray();
		assertThat(zipBytes)
				.as("controller must flush the streamed ZIP to the response body")
				.isNotEmpty();

		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry first = zis.getNextEntry();
			assertThat(first)
					.as("ZIP must contain at least one entry")
					.isNotNull();
			assertThat(first.getName())
					.as("manifest.json must be ZipEntry #0 — wire-contract invariant (RESEARCH §L-72.D-14)")
					.isEqualTo("manifest.json");
		}
	}
}
