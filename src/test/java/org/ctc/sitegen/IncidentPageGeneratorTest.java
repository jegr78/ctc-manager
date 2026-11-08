package org.ctc.sitegen;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.incidents.PublicIncidentCase;
import org.ctc.domain.model.Season;
import org.ctc.domain.service.IncidentPublicReadService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentPageGeneratorTest {
    @TempDir Path out;
    final IncidentPublicReadService reads = mock(IncidentPublicReadService.class);
    final TemplateWriter writer = mock(TemplateWriter.class);
    final SiteSlugger slugger = new SiteSlugger();
    final IncidentPageGenerator generator = new IncidentPageGenerator(reads, slugger, writer);

    @Test
    void givenPublicCases_whenGenerating_thenGroupByOwningMatchdayAndLinkToItsActualPage() throws Exception {
        var season = new Season();season.setId(UUID.randomUUID());season.setName("Test publication");season.setYear(2027);season.setNumber(1);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(reads.forSeason(season.getId())).thenReturn(List.of(item(second, "Round 2", 2), item(first, "Round 1", 1), item(first, "Round 1", 1)));
        var ctx = new GenerationContext(out, season, "active", "Active season", false, null, new SiteSlugs(Map.of(), Map.of(), List.of()));
        var result = new SiteGeneratorService.GenerationResult();

        generator.generate(ctx, result);

        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/incidents"), rendered.capture(), eq(out.resolve("season/2027-1-test-publication/incidents.html")), eq(out), eq("active"), eq("Active season"));
        var groups = (List<IncidentPageGenerator.MatchdayCases>) rendered.getValue().getVariable("incidentGroups");
        assertThat(groups).extracting(IncidentPageGenerator.MatchdayCases::label).containsExactly("Round 1", "Round 2");
        assertThat(groups.getFirst().cases()).hasSize(2);
        assertThat(groups.getFirst().href()).isEqualTo("matchday/round-1.html");
        assertThat(rendered.getValue().getVariable("caseCount")).isEqualTo(3);
        assertThat(result.getPagesGenerated()).isEqualTo(1);
    }

    private PublicIncidentCase item(UUID day, String label, int order) {
        return new PublicIncidentCase(UUID.randomUUID(), "INC-TEST", day, label, order, "T-ALF vs T-BRV", "Leg 1", "Test Alpha",
                "Alpha 1 / Bravo 3", "00:13.500", "Report", "Replay", "Final verdict", 1, Instant.parse("2026-11-08T15:00:00Z"));
    }
}
