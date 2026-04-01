package org.ctc.admin.service;

import org.ctc.admin.service.TemplatePreviewService.TemplateSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplatePreviewServiceTest {

    private TemplatePreviewService service;

    @BeforeEach
    void setUp() {
        service = new TemplatePreviewService();
    }

    @Test
    void givenTeamCardTemplate_whenRenderPreview_thenHtmlContainsSampleData() {
        // given
        String template = """
                <html><body>
                <span th:text="${teamName}"></span>
                <span th:text="${rating}"></span>
                <span th:text="${points}"></span>
                <span th:text="${record}"></span>
                <span th:text="${primaryColor}"></span>
                </body></html>
                """;

        // when
        String html = service.renderPreview("team-cards", template);

        // then
        assertThat(html).contains("Team Alpha");
        assertThat(html).contains("85");
        assertThat(html).contains("42");
        assertThat(html).contains("3 - 1 - 0");
        assertThat(html).contains("#E63946");
    }

    @Test
    void givenTeamCardTemplate_whenRenderPreview_thenLogoBase64IsCtcLogo() {
        // given
        String template = "<html><body><img th:src=\"${logoBase64}\"/></body></html>";

        // when
        String html = service.renderPreview("team-cards", template);

        // then
        assertThat(html).contains("data:image/png;base64,");
    }

    @Test
    void givenTeamCardTemplate_whenRenderPreview_thenFontBase64IsLoaded() {
        // given
        String template = "<html><style th:inline=\"text\">font: [[${fontBase64}]]</style></html>";

        // when
        String html = service.renderPreview("team-cards", template);

        // then
        assertThat(html).contains("data:font/woff2;base64,");
    }

    @Test
    void givenLineupTemplate_whenRenderPreview_thenContainsPairings() {
        // given
        String template = """
                <html><body>
                <span th:text="${seasonYear}"></span>
                <span th:text="${matchdayName}"></span>
                <div th:each="p : ${pairings}">
                    <span th:text="${p.homeDriver}"></span> vs <span th:text="${p.awayDriver}"></span>
                </div>
                </body></html>
                """;

        // when
        String html = service.renderPreview("lineup", template);

        // then
        assertThat(html).contains("2026");
        assertThat(html).contains("MD 1");
        assertThat(html).contains("Player_One");
        assertThat(html).contains("Player_Six");
        assertThat(html).contains("Player_Seven");
        assertThat(html).contains("Player_Twelve");
    }

    @Test
    void givenSettingsTemplate_whenRenderPreview_thenContainsRaceSettings() {
        // given
        String template = """
                <html><body>
                <span th:text="${carName}"></span>
                <span th:text="${trackName}"></span>
                <span th:text="${numberOfLaps}"></span>
                <span th:text="${weather}"></span>
                </body></html>
                """;

        // when
        String html = service.renderPreview("settings", template);

        // then
        assertThat(html).contains("Mazda RX-Vision GT3");
        assertThat(html).contains("Nürburgring 24h");
        assertThat(html).contains("5");
        assertThat(html).contains("Clear");
    }

    @Test
    void givenRaceResultsTemplate_whenRenderPreview_thenContainsResultRows() {
        // given
        String template = """
                <html><body>
                <span th:text="${homeTotal}"></span>
                <span th:text="${awayTotal}"></span>
                <span th:text="${homeIsWinner}"></span>
                <div th:each="row : ${resultRows}">
                    <span th:text="${row.homeDriver}"></span>: <span th:text="${row.homePoints}"></span>
                </div>
                </body></html>
                """;

        // when
        String html = service.renderPreview("race-results", template);

        // then
        assertThat(html).contains("Player_One");
        assertThat(html).contains("Player_Six");
        assertThat(html).contains("true"); // homeIsWinner
    }

    @Test
    void givenMatchdayOverviewTemplate_whenRenderPreview_thenContainsMatches() {
        // given
        String template = """
                <html><body>
                <span th:text="${data.matchdayLabel}"></span>
                <div th:each="m : ${data.matches}">
                    <span th:text="${m.homeTeamShortName}"></span> vs <span th:text="${m.awayTeamShortName}"></span>
                </div>
                </body></html>
                """;

        // when
        String html = service.renderPreview("matchday-overview", template);

        // then
        assertThat(html).contains("Match Day 3");
        assertThat(html).contains("ALF");
        assertThat(html).contains("BRV");
    }

    @Test
    void givenMatchdayScheduleTemplate_whenRenderPreview_thenContainsSchedule() {
        // given
        String template = """
                <html><body>
                <div th:each="m : ${data.matches}">
                    <span th:text="${m.scheduledDateTime}"></span>
                </div>
                </body></html>
                """;

        // when
        String html = service.renderPreview("matchday-schedule", template);

        // then
        assertThat(html).contains("Fri, 20 Mar. 19:30 GMT");
    }

    @Test
    void givenMatchdayResultsTemplate_whenRenderPreview_thenContainsScores() {
        // given
        String template = """
                <html><body>
                <div th:each="m : ${data.matches}">
                    <span th:text="${m.homeScore}"></span> - <span th:text="${m.awayScore}"></span>
                </div>
                </body></html>
                """;

        // when
        String html = service.renderPreview("matchday-results", template);

        // then
        assertThat(html).contains("54");
        assertThat(html).contains("42");
    }

    @Test
    void givenOverlayTemplate_whenRenderPreview_thenContainsTeamData() {
        // given
        String template = """
                <html><body>
                <span th:text="${homeTeamName}"></span>
                <span th:text="${awayTeamName}"></span>
                <span th:text="${homeRecord}"></span>
                <span th:text="${seasonYear}"></span>
                <span th:text="${matchdayName}"></span>
                </body></html>
                """;

        // when
        String html = service.renderPreview("overlay", template);

        // then
        assertThat(html).contains("Team Alpha");
        assertThat(html).contains("Team Bravo");
        assertThat(html).contains("3 - 1 - 0");
        assertThat(html).contains("2026");
        assertThat(html).contains("Match Day 3");
    }

    @Test
    void givenInvalidTemplateType_whenRenderPreview_thenThrowsException() {
        // when / then
        assertThatThrownBy(() -> service.renderPreview("invalid-type", "<html></html>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid-type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"team-cards", "lineup", "settings", "race-results",
            "matchday-overview", "matchday-schedule", "matchday-results", "overlay"})
    void givenAnyTemplateType_whenRenderPreview_thenReturnsNonEmptyHtml(String templateType) {
        // given
        String template = "<html><body>Test</body></html>";

        // when
        String html = service.renderPreview(templateType, template);

        // then
        assertThat(html).isNotEmpty();
        assertThat(html).contains("Test");
    }

    @Nested
    class TemplateSecurity {

        @ParameterizedTest
        @ValueSource(strings = {
                "<span th:text=\"${T(java.lang.Runtime).getRuntime()}\"></span>",
                "<span th:text=\"${T( java.lang.Runtime )}\"></span>"
        })
        void givenSpringElTypeAccess_whenValidate_thenRejectsTemplate(String template) {
            // when / then
            assertThatThrownBy(() -> service.validateTemplateContent(template))
                    .isInstanceOf(TemplateSecurityException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Runtime", "ProcessBuilder", "getClass(", "Class.forName",
                "ClassLoader", "URLClassLoader", "ScriptEngine",
                "javax.script", "java.lang.reflect"
        })
        void givenBlockedToken_whenValidate_thenRejectsTemplate(String token) {
            // given
            String template = "<span th:text=\"${" + token + "}\">";

            // when / then
            assertThatThrownBy(() -> service.validateTemplateContent(template))
                    .isInstanceOf(TemplateSecurityException.class);
        }

        @Test
        void givenOgnlStaticAccess_whenValidate_thenRejectsTemplate() {
            // given
            String template = "<span th:text=\"${@java.lang.System@getenv()}\">";

            // when / then
            assertThatThrownBy(() -> service.validateTemplateContent(template))
                    .isInstanceOf(TemplateSecurityException.class);
        }

        @Test
        void givenExpressionPreprocessing_whenValidate_thenRejectsTemplate() {
            // given
            String template = "<span th:text=\"__${malicious}__\">";

            // when / then
            assertThatThrownBy(() -> service.validateTemplateContent(template))
                    .isInstanceOf(TemplateSecurityException.class);
        }

        @Test
        void givenSafeTemplate_whenValidate_thenAcceptsTemplate() {
            // given
            String template = """
                    <html><body>
                    <span th:text="${teamName}"></span>
                    <div th:each="p : ${pairings}">
                        <span th:text="${p.homeDriver}"></span>
                    </div>
                    <style>@font-face { font-family: 'Test'; }</style>
                    </body></html>
                    """;

            // when / then (no exception)
            service.validateTemplateContent(template);
        }

        @Test
        void givenMaliciousTemplate_whenRenderPreview_thenThrowsSecurityException() {
            // given
            String template = "<span th:text=\"${T(java.lang.Runtime).getRuntime()}\"></span>";

            // when / then
            assertThatThrownBy(() -> service.renderPreview("team-cards", template))
                    .isInstanceOf(TemplateSecurityException.class);
        }
    }
}
