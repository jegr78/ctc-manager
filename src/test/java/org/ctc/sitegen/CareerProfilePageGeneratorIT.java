package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.TeamStatsService;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.testsupport.SitegenTestDir;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class CareerProfilePageGeneratorIT {
    static final java.nio.file.Path siteDir = SitegenTestDir.create("career-site");

    @org.springframework.test.context.DynamicPropertySource
    static void siteOutputDir(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", siteDir::toString);
    }

    @Autowired SiteGeneratorService generator;
    @Autowired CareerProfilePageGenerator careers;
    @Autowired SeasonRepository seasons;
    @Autowired SiteSlugService slugService;
    @Autowired SiteSlugger slugger;
    @Autowired DriverStatsService driverStats;
    @Autowired TeamStatsService teamStats;

    @Test
    void givenSeasonWithResults_whenGeneratingCareers_thenWriteTheStatsFiguresAndLinkEachSeasonProfile() throws IOException {
        // given
        var season = seasons.findByYearAndNumber(2026, 99).getFirst();
        var counted = List.of(season);
        var out = SitegenTestDir.create("career-profiles");
        var slugs = slugService.allocate();
        var period = StatsPeriod.of(null, counted, false);
        var expected = driverStats.ranked(period).values().iterator().next();
        // when
        var drivers = careers.generateDrivers(out, counted, slugs, "", "", new SiteGeneratorService.GenerationResult());
        var teams = careers.generateTeams(out, counted, slugs, "", "", new SiteGeneratorService.GenerationResult());
        // then
        assertThat(drivers).as("every ranked driver gets a career page").contains(expected.driverId());
        var page = Jsoup.parse(Files.readString(out.resolve("driver").resolve(slugs.driver(expected.driverId()) + ".html")));
        assertThat(page.selectFirst(".profile-total-points").text()).as("the career page shows the /stats points").isEqualTo(String.valueOf(expected.points()));
        assertThat(page.selectFirst(".career-rank").text()).isEqualTo(expected.rank() + " of " + expected.ranked());
        assertThat(page.select(".career-seasons a").attr("href")).as("each season row opens that season's profile")
                .isEqualTo("../season/" + slugger.slugify(season.getDisplayLabel()) + "/driver/" + slugs.driver(expected.driverId()) + ".html");
        assertThat(teams).isNotEmpty();
        var team = teams.iterator().next();
        var teamFigures = teamStats.stats(team.toString(), period);
        var teamPage = Jsoup.parse(Files.readString(out.resolve("team").resolve(slugs.team(team) + ".html")));
        assertThat(teamPage.selectFirst(".profile-team-points").text()).isEqualTo(String.valueOf(teamFigures.matchPoints()));
        assertThat(teamPage.select(".career-seasons a").attr("href"))
                .isEqualTo("../season/" + slugger.slugify(season.getDisplayLabel()) + "/team/" + slugs.team(team) + ".html");
        assertThat(teamPage.select("link[rel=stylesheet]").attr("href")).as("assets resolve from the root-level folder").startsWith("../assets/");
    }

    @Test
    void givenFullGeneration_whenDirectoriesAndAlltimePagesLinkProfiles_thenTheyOpenExistingCareerPages() throws IOException {
        // when
        var result = generator.generate();
        // then
        assertThat(result.getErrors()).isEmpty();
        for (String page : List.of("drivers.html", "alltime-driver-ranking.html", "teams.html", "alltime-standings.html")) {
            var links = Jsoup.parse(Files.readString(siteDir.resolve(page))).select("a[href^=driver/], a[href^=team/]");
            assertThat(links).as(page + " links career pages").isNotEmpty();
            links.forEach(link -> assertThat(siteDir.resolve(link.attr("href"))).as(page + " -> " + link.attr("href")).exists());
        }
    }
}
