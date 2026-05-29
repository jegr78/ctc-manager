package org.ctc.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:locsafetest;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.locations=classpath:db/migration",
		"logging.config=classpath:logback-test.xml"
})
@ActiveProfiles("local")
@Tag("integration")
class LocalProfileDataSafetyIT {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void givenLocalProfile_whenSpringContextLoaded_thenDevDataSeederAndTestDataServiceBeansAreAbsent() {
		// given — Spring context loaded under @ActiveProfiles("local") with H2 in-memory DataSource override

		// when
		String[] devDataSeederBeans = applicationContext.getBeanNamesForType(DevDataSeeder.class);
		String[] testDataServiceBeans = applicationContext.getBeanNamesForType(TestDataService.class);

		// then
		assertThat(devDataSeederBeans)
				.as("DevDataSeeder must NOT be a Spring bean under @ActiveProfiles(\"local\")")
				.isEmpty();
		assertThat(testDataServiceBeans)
				.as("TestDataService must NOT be a Spring bean under @ActiveProfiles(\"local\")")
				.isEmpty();
	}
}
