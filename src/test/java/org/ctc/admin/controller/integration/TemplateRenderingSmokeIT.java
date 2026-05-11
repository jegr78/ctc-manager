package org.ctc.admin.controller.integration;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * PLAT-06 smoke regression: GETs every /admin/** route discovered dynamically via
 * RequestMappingHandlerMapping and asserts that no route renders a TemplateProcessingException.
 * <p>
 * Assertion contract (D-11a relaxed):
 * <ul>
 *   <li>HTTP status {@literal <} 500 — admin routes legitimately redirect (302); the contract is
 *       "no template-rendering failure", not "every route returns 200".</li>
 *   <li>Response body does NOT match word-boundary-anchored regex
 *       {@code .*\bTemplateProcessingException\b.*} — catches Thymeleaf restricted-mode failures
 *       while avoiding false positives from documentation prose that contains the exception name
 *       as a non-isolated token (e.g., {@code "MyTemplateProcessingExceptionHandler"}).</li>
 * </ul>
 * <p>
 * Route discovery is dynamic (D-08): a new admin controller is automatically covered without
 * touching this class. Path variables are substituted with seeded fixture UUIDs (D-10) from
 * template-rendering-smoke-fixture.sql.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Sql(scripts = "/sql/template-rendering-smoke-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TemplateRenderingSmokeIT {

    /**
     * Word-boundary-anchored regex for TemplateProcessingException detection (per D-11 literal
     * directive + D-11a revised contract). The {@code \b} anchors prevent false positives from
     * narrative prose like {@code "MyTemplateProcessingExceptionHandler"} or documentation
     * strings containing the substring as a non-isolated token.
     */
    private static final String TEMPLATE_EX_REGEX = ".*\\bTemplateProcessingException\\b.*";

    // Path-variable substitution map (per CONTEXT.md D-10).
    // Keys cover every path-variable name any /admin/** route declares.
    // Values are the seeded UUIDs from template-rendering-smoke-fixture.sql.
    private static final Map<String, String> PATH_VARS = Map.ofEntries(
            Map.entry("id",             "00000000-0000-0071-0000-000000000010"), // generic id — defaults to season (most common)
            Map.entry("seasonId",       "00000000-0000-0071-0000-000000000010"),
            Map.entry("phaseId",        "00000000-0000-0071-0000-000000000011"),
            Map.entry("groupId",        "00000000-0000-0071-0000-000000000012"),
            Map.entry("teamId",         "00000000-0000-0071-0000-000000000020"),
            Map.entry("driverId",       "00000000-0000-0071-0000-000000000070"),
            Map.entry("matchdayId",     "00000000-0000-0071-0000-000000000050"),
            Map.entry("matchId",        "00000000-0000-0071-0000-000000000055"),
            Map.entry("raceId",         "00000000-0000-0071-0000-000000000060"),
            Map.entry("raceScoringId",  "00000000-0000-0071-0000-000000000001"),
            Map.entry("matchScoringId", "00000000-0000-0071-0000-000000000002"),
            Map.entry("sourceId",       "00000000-0000-0071-0000-000000000070"), // driver-merge source
            Map.entry("targetId",       "00000000-0000-0071-0000-000000000071"), // driver-merge target
            Map.entry("playoffId",      "00000000-0000-0071-0000-000000000099")  // playoff not in fixture; route may 404 cleanly
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * Discovers every /admin/** GET route via RequestMappingHandlerMapping introspection and
     * asserts each (a) returns HTTP status {@literal <} 500 and (b) response body does not match
     * the word-boundary-anchored pattern {@code \bTemplateProcessingException\b}.
     *
     * <p>Given the smoke fixture is seeded (via @Sql) and the Spring context is up
     * When the dynamic-test factory enumerates every admin GET route via RequestMappingHandlerMapping
     * Then for each route the test substitutes path variables with seeded UUIDs and performs GET
     * And asserts HTTP status {@literal <} 500 (per D-11a — allows 200, 3xx redirects, 4xx for unseeded paths)
     * And asserts response body does NOT match {@code .*\bTemplateProcessingException\b.*}
     */
    @TestFactory
    Stream<DynamicTest> givenSmokeFixtureSeeded_whenGetAllAdminRoutes_thenAllRenderWithoutTemplateProcessingException() {
        Set<String> patterns = new LinkedHashSet<>();

        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!supportsGet(info)) return;

            Set<String> ps;
            if (info.getPathPatternsCondition() != null) {
                ps = new LinkedHashSet<>(info.getPathPatternsCondition().getPatternValues());
            } else {
                ps = Collections.emptySet();
            }

            for (String p : ps) {
                if (p != null && p.startsWith("/admin")) {
                    patterns.add(p);
                }
            }
        });

        return patterns.stream()
                .map(p -> DynamicTest.dynamicTest("GET " + p, () -> assertRouteRenders(p)));
    }

    private static boolean supportsGet(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        // Empty methods set means all methods are accepted (e.g., @RequestMapping with no method constraint)
        return methods.isEmpty() || methods.contains(RequestMethod.GET);
    }

    private void assertRouteRenders(String pattern) throws Exception {
        String url = substitutePathVars(pattern);
        var response = mockMvc.perform(get(url)).andReturn().getResponse();

        int status = response.getStatus();
        String body = response.getContentAsString();

        // D-11a relaxed contract: status < 500 (allows 200, 3xx redirects, 4xx for unseeded paths).
        // A TemplateProcessingException would manifest as 500 — explicitly fail on 5xx.
        assertThat(status)
                .as("GET %s must not return 5xx (status was %d) — would indicate a TemplateProcessingException "
                        + "or other server error at runtime (PLAT-06)", url, status)
                .isLessThan(500);

        // D-11a word-boundary anchoring per D-11 literal directive: use doesNotMatch on the
        // regex .*\bTemplateProcessingException\b.* — prevents false positives from narrative
        // prose ("MyTemplateProcessingExceptionHandler") while still catching the bare exception
        // name when Thymeleaf surfaces it in an error page.
        assertThat(body)
                .as("GET %s response body must not contain \\bTemplateProcessingException\\b "
                        + "(word-boundary anchored per D-11a) — signals Thymeleaf restricted-mode "
                        + "rendering failure (PLAT-06)", url)
                .doesNotMatch(TEMPLATE_EX_REGEX);
    }

    private String substitutePathVars(String pattern) {
        String result = pattern;
        for (Map.Entry<String, String> entry : PATH_VARS.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        // Strip any remaining {placeholder} segments for path vars we did not seed:
        // substitute with seeded season UUID as best-effort default. A 404 page rendering
        // cleanly is the desired outcome — TemplateProcessingException assertion still runs.
        result = result.replaceAll("\\{[^/}]+\\}", PATH_VARS.get("id"));
        return result;
    }
}
