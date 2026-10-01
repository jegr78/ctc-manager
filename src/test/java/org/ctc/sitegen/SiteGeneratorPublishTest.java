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
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
				.as("entries next to the site").containsExactlyInAnyOrder("site", "site.lock");
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
				.as("entries next to the site").containsExactlyInAnyOrder("site", "site.lock");
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
	void givenGenerationInProgress_whenASecondOneStarts_thenItIsRejectedAndTheFirstPublishes() throws Exception {
		// given
		var firstIndexStarted = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		doAnswer(invocation -> {
			firstIndexStarted.countDown();
			releaseFirst.await(10, TimeUnit.SECONDS);
			Files.writeString(invocation.getArgument(2), "site/index");
			return null;
		}).when(templateWriter).write(eq("site/index"), any(), any(), any(), any(), any());
		writeOtherPagesForReal();
		var pool = Executors.newSingleThreadExecutor();
		var first = pool.submit(service::generate);
		assertThat(firstIndexStarted.await(10, TimeUnit.SECONDS)).as("first generation reached the index").isTrue();

		// when
		var second = service.generate();
		releaseFirst.countDown();

		// then
		assertThat(second.getErrors()).as("second generation errors")
				.containsExactly("A site generation is already running. Try again when it has finished.");
		assertThat(first.get(10, TimeUnit.SECONDS).getErrors()).as("first generation errors").isEmpty();
		pool.shutdown();
		assertThat(site.resolve("index.html")).as("published index").hasContent("site/index");
	}

	@Test
	void givenAnotherProcessHoldsTheSiteLock_whenGenerated_thenItIsRejectedAndTheSiteStays() throws Exception {
		// given
		writePagesForReal();
		try (var channel = FileChannel.open(parent.resolve("site.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		     var lock = channel.lock()) {

			// when
			var result = service.generate();

			// then
			assertThat(result.getErrors()).as("generation errors")
					.containsExactly("A site generation is already running. Try again when it has finished.");
		}
		assertThat(site.resolve("index.html")).as("previous index").hasContent("previous");
	}

	@Test
	void givenOutputDirectoryContainingTheUploads_whenGenerated_thenRefusedBeforeTouchingIt() throws Exception {
		// given
		service.setUploadDir(site.resolve("uploads").toString());
		writePagesForReal();

		// when
		var result = service.generate();

		// then
		assertThat(result.getErrors()).as("generation errors").singleElement().asString()
				.startsWith("Generation failed: Refusing to publish to " + site);
		assertThat(site.resolve("index.html")).as("previous index").hasContent("previous");
		assertThat(Files.list(parent).map(p -> p.getFileName().toString()).toList())
				.as("entries next to the site").containsExactly("site");
	}

	@Test
	void givenPublishInterruptedBetweenItsRenames_whenGeneratedAgainAndFailing_thenThePreviousSiteIsBack() throws Exception {
		// given
		Files.move(site, parent.resolve("site.previous"));
		writePagesForReal();
		doThrow(new IOException("disk full")).when(templateWriter)
				.write(eq("site/archive"), any(), any(), any(), any(), any());

		// when
		service.generate();

		// then
		assertThat(site.resolve("index.html")).as("restored index").hasContent("previous");
	}

	@Test
	void givenStagingLeftByACrashedRun_whenGenerated_thenItIsRemoved() throws Exception {
		// given
		Files.createDirectories(parent.resolve("site.generating-crashed"));
		writePagesForReal();

		// when
		var result = service.generate();

		// then
		assertThat(result.getErrors()).as("generation errors").isEmpty();
		assertThat(parent.resolve("site.generating-crashed")).as("stale staging").doesNotExist();
	}

	@Test
	void givenOutputOnAnotherFileStore_whenCopiedIn_thenItHoldsExactlyTheNewSite() throws Exception {
		// given
		Path staging = Files.createDirectories(parent.resolve("staging"));
		Files.createDirectories(staging.resolve("season/a"));
		Files.writeString(staging.resolve("index.html"), "new");
		Files.writeString(staging.resolve("season/a/team.html"), "team");
		Files.createDirectories(site.resolve("season/old"));
		Files.writeString(site.resolve("season/old/stale.html"), "stale");

		// when
		SiteGeneratorService.copyInto(staging, site);

		// then
		assertThat(site.resolve("index.html")).as("replaced page").hasContent("new");
		assertThat(site.resolve("season/a/team.html")).as("new nested page").hasContent("team");
		assertThat(site.resolve("season/old")).as("directory of the previous site").doesNotExist();
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
