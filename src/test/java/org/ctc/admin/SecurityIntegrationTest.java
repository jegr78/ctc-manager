package org.ctc.admin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
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

		@Test
		void givenNoSeparateManagementPort_whenAccessPrometheusAnonymously_thenUnauthorized() throws Exception {
			mockMvc.perform(get("/actuator/prometheus"))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"spring.datasource.url=jdbc:h2:mem:sectestmgmt;DB_CLOSE_DELAY=-1",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=validate",
			"spring.flyway.locations=classpath:db/migration",
			"logging.config=classpath:logback-test.xml",
			"management.server.port=0",
			"management.endpoints.web.exposure.include=health,prometheus",
			"management.endpoint.health.probes.add-additional-paths=true",
			"server.forward-headers-strategy=framework"
	})
	@ActiveProfiles("prod")
	class ProdManagementPortSecurityTest {

		@LocalServerPort
		private int serverPort;

		@LocalManagementPort
		private int managementPort;

		@Test
		void givenManagementPort_whenScrapePrometheusAnonymously_thenMetricsAreServed() {
			// when
			var response = anonymousGet(managementPort, "/actuator/prometheus");

			// then
			assertThat(response.status()).as("anonymous scrape on the management port").isEqualTo(200);
			assertThat(response.body()).as("Prometheus text format with JVM metrics").contains("jvm_memory_used_bytes");
		}

		@Test
		void givenManagementPort_whenAccessHealthAnonymously_thenOk() {
			// when
			var response = anonymousGet(managementPort, "/actuator/health");

			// then
			assertThat(response.status()).isEqualTo(200);
		}

		@Test
		void givenManagementPort_whenProbeReadinessOnApplicationPort_thenOk() {
			// when
			var response = anonymousGet(serverPort, "/readyz");

			// then
			assertThat(response.status()).as("the application port keeps an anonymous readiness probe").isEqualTo(200);
		}

		@Test
		void givenSpoofedForwardedPort_whenAccessPrometheusOnApplicationPort_thenUnauthorized() {
			// when
			var response = RestClient.create("http://127.0.0.1:" + serverPort).get().uri("/actuator/prometheus")
					.header("X-Forwarded-Port", String.valueOf(managementPort))
					.exchange((request, result) -> result.getStatusCode().value());

			// then
			assertThat(response).as("forwarded headers must not unlock metrics on the application port").isEqualTo(401);
		}

		@Test
		void givenManagementPort_whenAccessPrometheusOnApplicationPort_thenNotServed() {
			// when
			var response = anonymousGet(serverPort, "/actuator/prometheus");

			// then
			assertThat(response.status()).as("the application port never serves metrics").isEqualTo(401);
		}

		@Test
		void givenManagementPort_whenAccessAdminAnonymously_thenUnauthorized() {
			// when
			var response = anonymousGet(serverPort, "/admin/seasons");

			// then
			assertThat(response.status()).isEqualTo(401);
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"spring.datasource.url=jdbc:h2:mem:sectestsameport;DB_CLOSE_DELAY=-1",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=validate",
			"spring.flyway.locations=classpath:db/migration",
			"logging.config=classpath:logback-test.xml",
			"management.endpoints.web.exposure.include=health,prometheus"
	})
	@ActiveProfiles("prod")
	class ProdSharedPortSecurityTest {

		@LocalServerPort
		private int serverPort;

		@Test
		void givenNoSeparateManagementPort_whenScrapePrometheusAnonymously_thenUnauthorized() {
			// when
			var response = anonymousGet(serverPort, "/actuator/prometheus");

			// then
			assertThat(response.status()).as("metrics stay protected on a shared application port").isEqualTo(401);
		}
	}

	private static Response anonymousGet(int port, String path) {
		return RestClient.create("http://127.0.0.1:" + port).get().uri(path)
				.exchange((request, response) -> new Response(response.getStatusCode().value(),
						new String(response.getBody().readAllBytes())));
	}

	private record Response(int status, String body) {
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
