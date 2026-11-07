package org.ctc.sitegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.ctc.domain.model.Season;
import org.ctc.integration.OutboundPolicy;
import org.ctc.sitegen.publication.SitePublicationService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.Context;
import static org.assertj.core.api.Assertions.*;

class SeasonRuleDocumentsTest {
    @TempDir Path root;
    TemplateWriter writer;
    @BeforeEach
    void prepare() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        writer = new TemplateWriter(engine);
    }

    Path render(Season season, String slug, String file) throws Exception {
        var context = new Context(Locale.ENGLISH);
        context.setVariable("season", season);
        context.setVariable("seasonSlug", slug);
        context.setVariable("seasonName", season == null ? null : season.getName());
        context.setVariable("hasPlayoff", false);
        context.setVariable("currentPage", "links");
        context.setVariable("breadcrumbCurrent", "Test Documents");
        var community = new SiteProperties.LinkEntry();
        community.setName("Test Community"); community.setUrl("https://example.org/community");
        context.setVariable("links", List.of(community));
        var out = root.resolve(file); Files.createDirectories(out.getParent());
        writer.write("site/links", context, out, root, "current", "Test Current");
        return out;
    }

    @Test
    void twoSeasonsUseOwnDocumentsWithoutGlobalFallbackAndPreserveCommunityLinks() throws Exception {
        var current = new Season("Test Current", 2027, 1);
        current.setRulebookUrl("https://example.org/2027/rules?published=1&v=2");
        current.setRacingEtiquetteUrl("https://example.org/2027/etiquette");
        var archived = new Season("Test Archive", 2026, 1);
        archived.setRulebookUrl("https://example.org/2026/rules");
        archived.setRacingEtiquetteUrl("https://example.org/2026/etiquette");
        for (var season : List.of(current, archived)) {
            var output = render(season, "test-" + season.getYear(), season.getYear() + "/index.html");
            var doc = Jsoup.parse(Files.readString(output));
            var links = doc.select(".subnav a[target='_blank']");
            assertThat(links.eachAttr("href")).containsExactly(season.getRulebookUrl(), season.getRacingEtiquetteUrl());
            assertThat(links).allSatisfy(link -> {
                assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
                assertThat(link.text()).contains("opens in a new tab");
            });
            assertThat(doc.select(".link-card a").attr("href")).isEqualTo("https://example.org/community");
        }
        current.setRacingEtiquetteUrl(null);
        var one = Jsoup.parse(Files.readString(render(current, "test", "one.html")));
        assertThat(one.select(".subnav a[target='_blank']").eachAttr("href")).containsExactly(current.getRulebookUrl());
        current.setRulebookUrl(null);
        var empty = Jsoup.parse(Files.readString(render(current, "test", "empty.html")));
        assertThat(empty.select(".subnav a[target='_blank']")).isEmpty();
        var global = Jsoup.parse(Files.readString(render(archived, null, "global.html")));
        assertThat(global.select(".subnav")).isEmpty();
        assertThat(global.select("a[href*='/2026/']")).isEmpty();
    }

    @Test
    void unsafePersistedUrlCannotEnterGeneratedHtml() {
        var season = new Season("Test Unsafe"); season.setRulebookUrl("javascript:alert(1)");
        assertThatThrownBy(() -> render(season, "test", "unsafe.html")).isInstanceOf(IllegalArgumentException.class);
        assertThat(root.resolve("unsafe.html")).doesNotExist();
    }

    @Test
    void savingLinksLeavesGeneratedAndCapturedPagesUnchangedUntilExplicitGenerationAndPublication() throws Exception {
        var season = new Season("Test Snapshot"); season.setRulebookUrl("https://example.org/old-rules");
        var site = Files.createDirectory(root.resolve("site"));
        var output = render(season, "test", "site/index.html");
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        var publication = new SitePublicationService(root.resolve("publication").toString(), "test", true,
                environment, new OutboundPolicy(true, environment), new ObjectMapper());
        var first = publication.capture(site);
        season.setRulebookUrl("https://example.org/new-rules");
        assertThat(Files.readString(output)).contains("https://example.org/old-rules").doesNotContain("https://example.org/new-rules");
        render(season, "test", "site/index.html");
        var second = publication.capture(site);
        assertThat(Files.readString(publication.asset(first.id(), "index.html"))).contains("https://example.org/old-rules").doesNotContain("https://example.org/new-rules");
        assertThat(Files.readString(publication.asset(second.id(), "index.html"))).contains("https://example.org/new-rules");
        assertThat(root.resolve("publication/request.json")).doesNotExist();
        var request = publication.submit(first.id(), first.manifestSha256());
        assertThat(request.snapshotId()).isEqualTo(first.id());
        assertThat(request.manifestSha256()).isEqualTo(first.manifestSha256());
    }
}
