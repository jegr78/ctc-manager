package org.ctc.backup;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profile-conditional security IT for the Backup export endpoint.
 * Two nested classes: prod profile (SecurityConfig — auth + CSRF enforced)
 * and dev profile (OpenSecurityConfig — permit-all + CSRF disabled).
 * The prod-profile class uses its own isolated H2 schema to avoid collisions.
 */
@Tag("integration")
class BackupControllerSecurityIT {

	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:h2:mem:bksectest;DB_CLOSE_DELAY=-1",
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

		@Test
		void givenAnonymous_whenPostExport_thenUnauthorized() throws Exception {
			// Pass CSRF to isolate the auth path — CSRF filter fires before the auth filter on prod
			// and would otherwise return 403 (covered separately by givenAuthenticatedNoCsrf_...).
			mockMvc.perform(post("/admin/backup/export").with(anonymous()).with(csrf()))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void givenAnonymousNoCsrf_whenPostExport_thenForbidden() throws Exception {
			// Lock the layered defence: with no CSRF token, the CsrfFilter rejects with 403
			// BEFORE the AuthorizationFilter ever runs — this is the most common attacker
			// scenario (anonymous + missing token) and must remain 403 regardless of auth state.
			mockMvc.perform(post("/admin/backup/export").with(anonymous()))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedNoCsrf_whenPostExport_thenForbidden() throws Exception {
			// Default CSRF on prod profile rejects POSTs missing the _csrf token.
			mockMvc.perform(post("/admin/backup/export"))
					.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser
		void givenAuthenticatedWithCsrf_whenPostExport_thenOkWithContentDisposition() throws Exception {
			// StreamingResponseBody triggers Spring's async dispatch path; headers + status are
			// available on the initial response, the body is only written after asyncDispatch().
			mockMvc.perform(post("/admin/backup/export").with(csrf()))
					.andExpect(status().isOk())
					.andExpect(request().asyncStarted())
					.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
							Matchers.matchesPattern("attachment; filename=\"?ctc-backup-\\d{8}T\\d{6}Z\\.zip\"?")));
		}

		@Test
		void givenAnonymous_whenGetBackup_thenUnauthorized() throws Exception {
			mockMvc.perform(get("/admin/backup").with(anonymous()))
					.andExpect(status().isUnauthorized());
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
		void givenAnonymous_whenPostExport_thenOk() throws Exception {
			// OpenSecurityConfig disables CSRF and permits all requests on dev/local.
			mockMvc.perform(post("/admin/backup/export"))
					.andExpect(status().isOk());
		}

		@Test
		void givenAnonymous_whenGetBackup_thenOk() throws Exception {
			mockMvc.perform(get("/admin/backup"))
					.andExpect(status().isOk());
		}
	}
}
