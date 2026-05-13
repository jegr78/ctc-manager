package org.ctc.backup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 74-08 — profile-conditional security IT for the four backup import endpoints.
 *
 * <p>Mirrors {@link BackupControllerSecurityIT}'s two-{@code @Nested}-class shape:
 * <ul>
 *   <li>{@link ProdProfileSecurityTest} — {@code @ActiveProfiles("prod")}: {@code SecurityConfig}
 *       enforces authenticated + CSRF on {@code /admin/**}. Anonymous → 401 (with CSRF) or 403
 *       (without CSRF); authenticated no-CSRF → 403; authenticated with-CSRF → 3xx redirect
 *       (endpoint processes but redirects on rejection).</li>
 *   <li>{@link DevProfileSecurityTest} — {@code @ActiveProfiles("dev")}: {@code OpenSecurityConfig}
 *       permits-all + disables CSRF. Anonymous requests accepted (any 2xx or 3xx).</li>
 * </ul>
 *
 * <p>Uses isolated H2 {@code bkimpsectest} database (NOT shared with
 * {@link BackupControllerSecurityIT}'s {@code bksectest}) to avoid schema conflicts between
 * parallel inner-class Spring contexts.
 */
class BackupImportControllerSecurityIT {

	/** Minimal valid ZIP magic bytes — enough to pass the CSRF/auth layer (not ZIP validation). */
	private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};

	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:h2:mem:bkimpsectest;DB_CLOSE_DELAY=-1",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=validate",
			"spring.flyway.locations=classpath:db/migration",
			"logging.config=classpath:logback-test.xml"
	})
	@AutoConfigureMockMvc
	@ActiveProfiles("prod")
	class ProdProfileSecurityTest {

		@Autowired
		private MockMvc mockMvc;

		// -----------------------------------------------------------------------
		// POST /admin/backup/import-preview
		// -----------------------------------------------------------------------

		@Test
		void givenAnonymous_whenPostImportPreview_thenUnauthorized() throws Exception {
			// CSRF token present — isolates auth path from CSRF filter (same pattern as BackupControllerSecurityIT)
			MockMultipartFile file = new MockMultipartFile("file", "test.zip",
					"application/zip", ZIP_MAGIC);
			mockMvc.perform(multipart("/admin/backup/import-preview")
							.file(file)
							.with(anonymous())
							.with(csrf()))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void givenAnonymousNoCsrf_whenPostImportPreview_thenForbidden() throws Exception {
			MockMultipartFile file = new MockMultipartFile("file", "test.zip",
					"application/zip", ZIP_MAGIC);
			mockMvc.perform(multipart("/admin/backup/import-preview")
							.file(file)
							.with(anonymous()))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedNoCsrf_whenPostImportPreview_thenForbidden() throws Exception {
			MockMultipartFile file = new MockMultipartFile("file", "test.zip",
					"application/zip", ZIP_MAGIC);
			mockMvc.perform(multipart("/admin/backup/import-preview")
							.file(file))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedWithCsrf_whenPostImportPreview_thenProcessed() throws Exception {
			// The endpoint processes the request (which may fail ZIP validation and redirect) —
			// the security layer lets it through. Any 3xx or 4xx from the controller is acceptable here.
			MockMultipartFile file = new MockMultipartFile("file", "test.zip",
					"application/zip", ZIP_MAGIC);
			mockMvc.perform(multipart("/admin/backup/import-preview")
							.file(file)
							.with(csrf()))
					.andExpect(status().is3xxRedirection());
		}

		// -----------------------------------------------------------------------
		// POST /admin/backup/import-confirm
		// -----------------------------------------------------------------------

		@Test
		void givenAnonymous_whenPostImportConfirm_thenUnauthorized() throws Exception {
			mockMvc.perform(post("/admin/backup/import-confirm")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(anonymous())
							.with(csrf()))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void givenAnonymousNoCsrf_whenPostImportConfirm_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-confirm")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(anonymous()))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedNoCsrf_whenPostImportConfirm_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-confirm")
							.param("stagingId", "00000000-0000-0000-0000-000000000001"))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedWithCsrf_whenPostImportConfirm_thenProcessed() throws Exception {
			// Staging file absent → MANIFEST_MISSING → redirect to /admin/backup
			mockMvc.perform(post("/admin/backup/import-confirm")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(csrf()))
					.andExpect(status().is3xxRedirection());
		}

		// -----------------------------------------------------------------------
		// POST /admin/backup/import-execute
		// -----------------------------------------------------------------------

		@Test
		void givenAnonymous_whenPostImportExecute_thenUnauthorized() throws Exception {
			mockMvc.perform(post("/admin/backup/import-execute")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.param("acknowledged", "true")
							.with(anonymous())
							.with(csrf()))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void givenAnonymousNoCsrf_whenPostImportExecute_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-execute")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.param("acknowledged", "true")
							.with(anonymous()))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedNoCsrf_whenPostImportExecute_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-execute")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.param("acknowledged", "true"))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedWithCsrf_whenPostImportExecute_thenProcessed() throws Exception {
			// acknowledged=true but staging file absent → redirect to /admin/backup
			mockMvc.perform(post("/admin/backup/import-execute")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.param("acknowledged", "true")
							.with(csrf()))
					.andExpect(status().is3xxRedirection());
		}

		// -----------------------------------------------------------------------
		// POST /admin/backup/import-cancel
		// -----------------------------------------------------------------------

		@Test
		void givenAnonymous_whenPostImportCancel_thenUnauthorized() throws Exception {
			mockMvc.perform(post("/admin/backup/import-cancel")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(anonymous())
							.with(csrf()))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void givenAnonymousNoCsrf_whenPostImportCancel_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-cancel")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(anonymous()))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedNoCsrf_whenPostImportCancel_thenForbidden() throws Exception {
			mockMvc.perform(post("/admin/backup/import-cancel")
							.param("stagingId", "00000000-0000-0000-0000-000000000001"))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedWithCsrf_whenPostImportCancel_thenRedirects() throws Exception {
			// deleteStagingFile is idempotent (no-op for missing file) → always redirects
			mockMvc.perform(post("/admin/backup/import-cancel")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.with(csrf()))
					.andExpect(status().is3xxRedirection());
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("dev")
	class DevProfileSecurityTest {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void givenAnonymous_whenPostImportPreview_thenProcessedWithoutAuth() throws Exception {
			// OpenSecurityConfig disables CSRF and permits all — upload fails ZIP validation
			// and redirects, but the security layer does not block it
			MockMultipartFile file = new MockMultipartFile("file", "test.zip",
					"application/zip", ZIP_MAGIC);
			mockMvc.perform(multipart("/admin/backup/import-preview")
							.file(file))
					.andExpect(status().is3xxRedirection());
		}

		@Test
		void givenAnonymous_whenPostImportConfirm_thenProcessedWithoutAuth() throws Exception {
			mockMvc.perform(post("/admin/backup/import-confirm")
							.param("stagingId", "00000000-0000-0000-0000-000000000001"))
					.andExpect(status().is3xxRedirection());
		}

		@Test
		void givenAnonymous_whenPostImportExecute_thenProcessedWithoutAuth() throws Exception {
			mockMvc.perform(post("/admin/backup/import-execute")
							.param("stagingId", "00000000-0000-0000-0000-000000000001")
							.param("acknowledged", "true"))
					.andExpect(status().is3xxRedirection());
		}

		@Test
		void givenAnonymous_whenPostImportCancel_thenRedirectsWithoutAuth() throws Exception {
			mockMvc.perform(post("/admin/backup/import-cancel")
							.param("stagingId", "00000000-0000-0000-0000-000000000001"))
					.andExpect(status().is3xxRedirection());
		}
	}
}
