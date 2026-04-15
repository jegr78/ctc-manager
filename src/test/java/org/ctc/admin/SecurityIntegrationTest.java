package org.ctc.admin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest {

	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:h2:mem:sectest;DB_CLOSE_DELAY=-1",
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
		void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception {
			mockMvc.perform(get("/admin/seasons"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@WithMockUser
		void givenValidCredentials_whenAccessAdmin_thenOk() throws Exception {
			mockMvc.perform(get("/admin/seasons"))
					.andExpect(status().isOk());
		}

		@Test
		void givenNoCredentials_whenAccessHealth_thenOk() throws Exception {
			mockMvc.perform(get("/actuator/health"))
					.andExpect(status().isOk());
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
		void givenDevProfile_whenAccessAdmin_thenOk() throws Exception {
			mockMvc.perform(get("/admin/seasons"))
					.andExpect(status().isOk());
		}
	}
}
