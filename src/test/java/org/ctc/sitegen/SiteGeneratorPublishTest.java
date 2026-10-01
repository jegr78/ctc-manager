package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.SiteSlugs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteGeneratorPublishTest {

	@TempDir
	Path parent;

	private final SeasonRepository seasonRepository = mock(SeasonRepository.class);
	private final StandingsService standingsService = mock(StandingsService.class);
	private final DriverRankingService driverRankingService = mock(DriverRankingService.class);
	private final SiteProperties siteProperties = mock(SiteProperties.class);
	private final TemplateWriter templateWriter = mock(TemplateWriter.class);
	private final SiteSlugService siteSlugService = mock(SiteSlugService.class);
	private SiteGeneratorService service;
	private Path site;

	@BeforeEach
	void setUp() throws IOException {
		site = Files.createDirectories(parent.resolve("site"));
		Files.writeString(site.resolve("index.html"), "previous");
		lenient().when(siteProperties.getOutputDir()).thenReturn(site.toString());
		lenient().when(siteProperties.getLinks()).thenReturn(List.of());
		lenient().when(seasonRepository.findByActiveTrue()).thenReturn(Optional.empty());
		lenient().when(seasonRepository.findAll()).thenReturn(List.of());
		lenient().when(standingsService.calculateAlltimeStandings(anyList())).thenReturn(List.of());
		lenient().when(driverRankingService.calculateAlltimeRanking(anyList())).thenReturn(List.of());
		lenient().when(siteSlugService.allocate()).thenReturn(new SiteSlugs(Map.of(), Map.of(), List.of()));
		service = new SiteGeneratorService(seasonRepository, mock(SeasonDriverRepository.class), standingsService,
				driverRankingService, mock(PlayoffBracketViewService.class), mock(PlayoffRepository.class),
				mock(SeasonTeamRepository.class), siteProperties, mock(YouTubeScraperService.class),
				mock(SeasonPhaseService.class), new SiteSlugger(), templateWriter,
				mock(StandingsPageGenerator.class), mock(DriverRankingPageGenerator.class),
				mock(MatchdaysPageGenerator.class), mock(TeamProfilePageGenerator.class),
				mock(DriverProfilePageGenerator.class), siteSlugService);
	}

	@Test
	void givenPublishedSite_whenGenerationSucceeds_thenTheNewSiteReplacesItCompletely() throws Exception {
		// given
		Files.writeString(site.resolve("stale.html"), "stale");
		writePagesForReal();

		// when
		var result = service.generate();

		// then
		assertThat(result.getErrors()).as("generation errors").isEmpty();
		assertThat(site.resolve("index.html")).as("published index").hasContent("site/index");
		assertThat(site.resolve("stale.html")).as("page of the previous site").doesNotExist();
		assertThat(Files.list(parent).map(p -> p.getFileName().toString()).toList())
				.as("directories next to the site").containsExactly("site");
	}

	@Test
	void givenPublishedSite_whenAPageFailsMidGeneration_thenThePreviousSiteStaysAndNothingIsLeftOver()
			throws Exception {
		// given
		writePagesForReal();
		doThrow(new IOException("disk full")).when(templateWriter)
				.write(eq("site/archive"), any(), any(), any(), any(), any());

		// when
		var result = service.generate();

		// then
		assertThat(result.getErrors()).as("generation errors").containsExactly("Generation failed: disk full");
		assertThat(site.resolve("index.html")).as("previous index").hasContent("previous");
		assertThat(Files.list(parent).map(p -> p.getFileName().toString()).toList())
				.as("directories next to the site").containsExactly("site");
	}

	@Test
	void givenPublishedSite_whenAnIncompleteSiteIsGenerated_thenItIsNotPublished() throws Exception {
		// given
		doAnswer(invocation -> null).when(templateWriter).write(anyString(), any(), any(), any(), any(), any());

		// when
		var result = service.generate();

		// then
		assertThat(result.getErrors()).as("generation errors")
				.containsExactly("Generation failed: Generated site is incomplete: index.html is missing");
		assertThat(site.resolve("index.html")).as("previous index").hasContent("previous");
	}

	@Test
	void givenGenerationInProgress_whenASecondOneStarts_thenItWaitsAndBothPublishCompleteSites() throws Exception {
		// given
		var firstIndexStarted = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		var indexWrites = new AtomicInteger();
		doAnswer(invocation -> {
			Path file = invocation.getArgument(2);
			if (indexWrites.incrementAndGet() == 1) {
				firstIndexStarted.countDown();
				releaseFirst.await(10, TimeUnit.SECONDS);
			}
			Files.writeString(file, "site/index");
			return null;
		}).when(templateWriter).write(eq("site/index"), any(), any(), any(), any(), any());
		writeOtherPagesForReal();
		var pool = Executors.newFixedThreadPool(2);

		// when
		var first = pool.submit(service::generate);
		assertThat(firstIndexStarted.await(10, TimeUnit.SECONDS)).as("first generation reached the index").isTrue();
		var second = pool.submit(service::generate);
		Thread.sleep(300);
		int writesWhileFirstRuns = indexWrites.get();
		releaseFirst.countDown();

		// then
		assertThat(writesWhileFirstRuns).as("index writes while the first generation runs").isEqualTo(1);
		assertThat(first.get(10, TimeUnit.SECONDS).getErrors()).as("first generation errors").isEmpty();
		assertThat(second.get(10, TimeUnit.SECONDS).getErrors()).as("second generation errors").isEmpty();
		pool.shutdown();
		assertThat(site.resolve("index.html")).as("published index").hasContent("site/index");
	}

	private void writePagesForReal() throws IOException {
		doAnswer(invocation -> {
			Path file = invocation.getArgument(2);
			Files.writeString(file, (String) invocation.getArgument(0));
			return null;
		}).when(templateWriter).write(anyString(), any(), any(), any(), any(), any());
	}

	private void writeOtherPagesForReal() throws IOException {
		doAnswer(invocation -> {
			Path file = invocation.getArgument(2);
			Files.writeString(file, (String) invocation.getArgument(0));
			return null;
		}).when(templateWriter).write(eq("site/archive"), any(), any(), any(), any(), any());
	}
}
