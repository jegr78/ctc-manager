package org.ctc.admin.controller.integration;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke regression for the Thymeleaf bracket-indexer bug class
 * (`th:text="${labels[enumKey]}"` against `Map<Enum, String>` resolves to null
 * and renders an `<option>` with the correct value attribute but empty text).
 * For each curated admin form GET URL, parses the rendered HTML and asserts
 * that every `<option>` carrying a non-empty value attribute also carries
 * non-empty text content. Intentional placeholder options
 * (`<option value="">— Select —</option>`) are excluded by the value-non-empty
 * filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class AdminDropdownRenderingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;
    @Autowired private SeasonPhaseRepository seasonPhaseRepository;

    private Season season;
    private SeasonPhase regularPhase;

    @BeforeEach
    void setUp() {
        season = testHelper.createSeason("T-DropdownSmoke-" + UUID.randomUUID().toString().substring(0, 6));
        regularPhase = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
    }

    static Stream<Arguments> dropdownFormUrls() {
        return Stream.of(
                arguments("/admin/seasons/new", "season-form (new)"),
                arguments("/admin/seasons/{sid}/edit", "season-form (edit)"),
                arguments("/admin/seasons/{sid}/phases/new", "season-phase-form (new)"),
                arguments("/admin/seasons/{sid}/phases/{pid}/edit", "season-phase-form (edit)"),
                arguments("/admin/playoffs/new?seasonId={sid}", "playoff-form")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("dropdownFormUrls")
    void givenAdminFormUrl_whenGet_thenAllValueBearingOptionsHaveNonEmptyText(
            String urlTemplate, String formName) throws Exception {
        String url = urlTemplate
                .replace("{sid}", season.getId().toString())
                .replace("{pid}", regularPhase.getId().toString());

        var html = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Element> brokenOptions = Jsoup.parse(html).select("option[value]").stream()
                .filter(opt -> !opt.attr("value").isBlank() && opt.text().isBlank())
                .toList();

        assertThat(brokenOptions)
                .as("Form %s (%s) renders <option value=\"X\"> elements with empty text — "
                        + "Thymeleaf bracket-indexer regression (use ${map.get(key)} not ${map[key]})",
                        formName, url)
                .isEmpty();
    }
}
