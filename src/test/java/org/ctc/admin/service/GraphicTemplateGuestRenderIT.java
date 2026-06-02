package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.ctc.admin.service.LineupGraphicService.DriverPairing;
import org.ctc.admin.service.ProvisionalScoresGraphicService.ProvisionalRow;
import org.ctc.admin.service.ResultsGraphicService.DriverResultRow;
import org.ctc.sitegen.YouTubeScraperService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the three admin graphic templates through the real Spring {@link TemplateEngine}
 * (the same SpEL-backed engine used in production — record-component accessors like
 * {@code isGuest()} only resolve there, not via a mocked or plain Thymeleaf engine). The
 * per-service unit tests all mock the engine, so this is the only coverage that the guest
 * marker actually binds and renders. Each case mixes one guest and one non-guest row to
 * prove the {@code th:if} evaluates the flag rather than always firing.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class GraphicTemplateGuestRenderIT {

	private static final String STAR = "&#x2605;";

	@Autowired private TemplateEngine templateEngine;

	@MockitoBean private YouTubeScraperService youTubeScraperService;

	private static int markerCount(String html) {
		return html.split(STAR, -1).length - 1;
	}

	@Test
	void givenGuestRow_whenRenderingProvisionalScores_thenStarMarkerRenderedForGuestOnly() {
		// given
		Context ctx = new Context();
		ctx.setVariable("homeRows", List.of(
				new ProvisionalRow("GuestDriver", 1, 1, false, 25, 5, 0, 30, true),
				new ProvisionalRow("LeagueDriver", 2, 2, false, 18, 4, 0, 22, false)));
		ctx.setVariable("awayRows", List.of());

		// when
		String html = templateEngine.process("admin/provisional-scores-render", ctx);

		// then
		assertThat(html).contains("guest-marker");
		assertThat(markerCount(html)).isEqualTo(1);
	}

	@Test
	void givenGuestPairing_whenRenderingLineup_thenStarMarkerRenderedForGuestOnly() {
		// given
		Context ctx = new Context();
		ctx.setVariable("pairings", List.of(
				new DriverPairing("GuestDriver", "", "LeagueDriver", "", true, false)));

		// when
		String html = templateEngine.process("admin/lineup-render", ctx);

		// then
		assertThat(html).contains("guest-marker");
		assertThat(markerCount(html)).isEqualTo(1);
	}

	@Test
	void givenGuestResultRow_whenRenderingResults_thenStarMarkerRenderedForGuestOnly() {
		// given
		Context ctx = new Context();
		ctx.setVariable("resultRows", List.of(
				new DriverResultRow("GuestDriver", "", 30, 20, "LeagueDriver", "", true, false)));
		ctx.setVariable("homeIsWinner", false);
		ctx.setVariable("awayIsWinner", false);

		// when
		String html = templateEngine.process("admin/results-render", ctx);

		// then
		assertThat(html).contains("guest-marker");
		assertThat(markerCount(html)).isEqualTo(1);
	}
}
