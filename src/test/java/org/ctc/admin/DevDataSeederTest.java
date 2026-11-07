package org.ctc.admin;

import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteGeneratorService.GenerationResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class DevDataSeederTest {
    private final TestDataService data = mock(TestDataService.class);
    private final SiteGeneratorService site = mock(SiteGeneratorService.class);
    private final RaceLineupRepository lineups = mock(RaceLineupRepository.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(application -> application.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME))
            .withPropertyValues("spring.profiles.active=dev")
            .withUserConfiguration(DevDataSeeder.class)
            .withBean(TestDataService.class, () -> data)
            .withBean(SiteGeneratorService.class, () -> site)
            .withBean(RaceLineupRepository.class, () -> lineups);

    @Test
    void givenDefaultDevConfiguration_whenStarting_thenSeedAndGenerateTheWebsite() {
        // given
        when(site.generate()).thenReturn(new GenerationResult());
        // when
        context.run(application -> {
            assertThat(application).hasNotFailed();
            application.getBean(DevDataSeeder.class).run();
            // then
            verify(data).seed();
            verify(lineups).findAll();
            verify(site).generate();
        });
    }

    @Test
    void givenAutomaticGenerationDisabled_whenStarting_thenKeepFixturesAndGuestValidationWithoutRendering() {
        // when
        context.withPropertyValues("app.seed.generate-site=false").run(application -> {
            assertThat(application).hasNotFailed();
            application.getBean(DevDataSeeder.class).run();
            // then
            verify(data).seed();
            verify(lineups).findAll();
            verifyNoInteractions(site);
        });
    }

    @Test
    void givenGenerationFailureWithDefaultConfiguration_whenStarting_thenKeepStartupAndSeedBehavior() {
        // given
        when(site.generate()).thenThrow(new IllegalStateException("synthetic render failure"));
        // when / then
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThatCode(() -> application.getBean(DevDataSeeder.class).run()).doesNotThrowAnyException();
            verify(data).seed();
            verify(lineups).findAll();
            verify(site).generate();
        });
    }
}
