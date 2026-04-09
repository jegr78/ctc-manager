package org.ctc.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataService {

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:data/dev/uploads}")
    private String uploadDir;

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchRepository matchRepository;
    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final TeamCardService teamCardService;

    @Transactional
    public void seed() {
        if (seasonRepository.count() > 0) {
            log.debug("Seed data already present, skipping");
            return;
        }
        var scorings = seedScorings();
        var teams = seedTeams();
        seedSubTeams(teams);
        copyDemoLogos(teams);
        var activeSeason = seedSeasons(teams, scorings);
        seedDrivers();
        seedAliases();
        seedSeasonDrivers();
        seedRaceLineups();
        seedTeamCards(activeSeason);
        log.info("Seed data created: {} teams, {} seasons, {} drivers, {} race-lineups",
                teamRepository.count(), seasonRepository.count(), driverRepository.count(),
                raceLineupRepository.count());
    }

    private record ScoringDefaults(RaceScoring raceScoring, MatchScoring matchScoring) {}

    private ScoringDefaults seedScorings() {
        var raceScoring = raceScoringRepository.save(
                new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("Standard 3-1-0", 3, 1, 0));
        log.info("Created default scoring presets: {} / {}", raceScoring.getName(), matchScoring.getName());
        return new ScoringDefaults(raceScoring, matchScoring);
    }

    private List<Team> seedTeams() {
        var teams = teamRepository.saveAll(List.of(
                team("Velocity Racing", "VRX", "#e63946", "#1d3557", "#f1faee"),
                team("Shadow Grid Motorsport", "SGM", "#2d2d2d", "#ff6b35", "#ffffff"),
                team("Apex Drift Racing", "ADR", "#06d6a0", "#073b4c", "#ffffff"),
                team("Thunderbolt Raceworks", "TBR", "#ffd166", "#ef476f", "#073b4c"),
                team("Iron Circuit League", "ICL", "#118ab2", "#ffffff", "#073b4c"),
                team("Stellar Velocity Team", "SVT", "#7209b7", "#f72585", "#ffffff"),
                team("Nitro Forge Racing", "NFR", "#ff9f1c", "#2ec4b6", "#000000"),
                team("Eclipse Grand Prix", "EGP", "#480ca8", "#f8961e", "#ffffff"),
                team("Horizon Motorsport", "HMS", "#c1121f", "#fdf0d5", "#003049"),
                team("Pulse Wave Racing", "PWR", "#00b4d8", "#90e0ef", "#03045e")
        ));
        return teams;
    }

    private Team subTeam(String name, String shortName, Team parent) {
        return subTeam(name, shortName, parent, parent.getPrimaryColor(), parent.getSecondaryColor(), parent.getAccentColor());
    }

    private Team subTeam(String name, String shortName, Team parent, String primary, String secondary, String accent) {
        var t = new Team(name, shortName, parent);
        t.setPrimaryColor(primary);
        t.setSecondaryColor(secondary);
        t.setAccentColor(accent);
        return t;
    }

    private Team team(String name, String shortName, String primary, String secondary, String accent) {
        var t = new Team(name, shortName);
        t.setPrimaryColor(primary);
        t.setSecondaryColor(secondary);
        t.setAccentColor(accent);
        return t;
    }

    private void seedSubTeams(List<Team> teams) {
        // VRX: 2 sub-teams
        var vrx = teams.stream().filter(t -> t.getShortName().equals("VRX")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "VRX"));
        teamRepository.save(subTeam("Velocity Racing Alpha", "VRX A", vrx));
        teamRepository.save(subTeam("Velocity Racing Beta", "VRX B", vrx, "#d62828", "#1d3557", "#f1faee"));

        // SGM: 2 sub-teams
        var sgm = teams.stream().filter(t -> t.getShortName().equals("SGM")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "SGM"));
        teamRepository.save(subTeam("Shadow Grid Black", "SGM B", sgm, "#1a1a1a", "#ff6b35", "#ffffff"));
        teamRepository.save(subTeam("Shadow Grid Silver", "SGM S", sgm, "#c0c0c0", "#ff6b35", "#2d2d2d"));

        // TBR: 3 sub-teams
        var tbr = teams.stream().filter(t -> t.getShortName().equals("TBR")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "TBR"));
        teamRepository.save(subTeam("Thunderbolt Red", "TBR R", tbr, "#ef476f", "#ffd166", "#073b4c"));
        teamRepository.save(subTeam("Thunderbolt Blue", "TBR B", tbr, "#118ab2", "#ffd166", "#ffffff"));
        teamRepository.save(subTeam("Thunderbolt Gold", "TBR G", tbr));

        log.info("Created sub-teams: VRX(2), SGM(2), TBR(3)");
    }

    private Season seedSeasons(List<Team> parentTeams, ScoringDefaults scorings) {
        var allTeams = teamRepository.findAll();

        // Helper to find parent team (no parent) by shortName
        java.util.function.Function<String, Team> findParent = (shortName) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        // Helper to find sub-team by shortName (has parent)
        java.util.function.Function<String, Team> findSub = (shortName) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        // Older seasons: all parent teams
        for (var entry : List.of(
                new Object[]{"Group A", 2023, 1, "Group A, Regular Season"},
                new Object[]{"Group B", 2023, 1, "Group B, Regular Season"},
                new Object[]{"Regular Season", 2024, 2, "Round Robin"})) {
            var season = createSeason((String) entry[0], (int) entry[1], (int) entry[2], (String) entry[3], scorings);
            parentTeams.forEach(season::addTeam);
            seasonRepository.save(season);
        }

        // Season 3 - 2025 - Group A: VRX A, ADR, ICL, NFR, HMS
        var s3a = createSeason("Group A", 2025, 3, "Group A, Regular Season", scorings);
        List.of(
                findSub.apply("VRX A"),
                findParent.apply("ADR"),
                findParent.apply("ICL"),
                findParent.apply("NFR"),
                findParent.apply("HMS")
        ).forEach(s3a::addTeam);
        seasonRepository.save(s3a);

        // Season 3 - 2025 - Group B: VRX parent + VRX B, TBR, EGP, PWR
        var s3b = createSeason("Group B", 2025, 3, "Group B, Regular Season", scorings);
        List.of(
                findParent.apply("VRX"),
                findSub.apply("VRX B"),
                findParent.apply("TBR"),
                findParent.apply("EGP"),
                findParent.apply("PWR")
        ).forEach(s3b::addTeam);
        seasonRepository.save(s3b);

        // Season 4 - 2026: all parents with subs + standalone parents
        var s4 = createSeason("Regular Season", 2026, 4, null, scorings);
        s4.setActive(true);
        List.of(
                findParent.apply("VRX"),
                findSub.apply("VRX A"),
                findSub.apply("VRX B"),
                findParent.apply("SGM"),
                findSub.apply("SGM B"),
                findSub.apply("SGM S"),
                findParent.apply("TBR"),
                findSub.apply("TBR R"),
                findSub.apply("TBR B"),
                findSub.apply("TBR G"),
                findParent.apply("ADR"),
                findParent.apply("ICL"),
                findParent.apply("SVT"),
                findParent.apply("NFR"),
                findParent.apply("EGP"),
                findParent.apply("HMS"),
                findParent.apply("PWR")
        ).forEach(s4::addTeam);
        seasonRepository.save(s4);

        // Set ratings for active season
        s4.findSeasonTeam(findSub.apply("VRX A")).ifPresent(st -> st.setRating(92));
        s4.findSeasonTeam(findSub.apply("VRX B")).ifPresent(st -> st.setRating(87));
        s4.findSeasonTeam(findSub.apply("SGM B")).ifPresent(st -> st.setRating(90));
        s4.findSeasonTeam(findSub.apply("SGM S")).ifPresent(st -> st.setRating(85));
        s4.findSeasonTeam(findSub.apply("TBR R")).ifPresent(st -> st.setRating(93));
        s4.findSeasonTeam(findSub.apply("TBR B")).ifPresent(st -> st.setRating(88));
        s4.findSeasonTeam(findSub.apply("TBR G")).ifPresent(st -> st.setRating(86));
        s4.findSeasonTeam(findParent.apply("ADR")).ifPresent(st -> st.setRating(84));
        s4.findSeasonTeam(findParent.apply("ICL")).ifPresent(st -> st.setRating(87));
        s4.findSeasonTeam(findParent.apply("SVT")).ifPresent(st -> st.setRating(91));
        s4.findSeasonTeam(findParent.apply("NFR")).ifPresent(st -> st.setRating(83));
        s4.findSeasonTeam(findParent.apply("EGP")).ifPresent(st -> st.setRating(89));
        s4.findSeasonTeam(findParent.apply("HMS")).ifPresent(st -> st.setRating(85));
        s4.findSeasonTeam(findParent.apply("PWR")).ifPresent(st -> st.setRating(88));
        seasonRepository.save(s4);

        return s4;
    }

    private void copyDemoLogos(List<Team> parentTeams) {
        var allTeams = teamRepository.findAll();
        Path uploadBase = Paths.get(uploadDir, "teams").toAbsolutePath().normalize();
        int copied = 0;
        for (var team : allTeams) {
            String logoKey = team.isSubTeam() ? team.getParentTeam().getShortName() : team.getShortName();
            try {
                var resource = new ClassPathResource("demo/team-logos/" + logoKey + ".png");
                if (resource.exists()) {
                    Path teamDir = uploadBase.resolve(team.getId().toString());
                    Files.createDirectories(teamDir);
                    Path target = teamDir.resolve(logoKey + ".png");
                    try (var is = resource.getInputStream()) {
                        Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    team.setLogoUrl("/uploads/teams/" + team.getId() + "/" + logoKey + ".png");
                    teamRepository.save(team);
                    copied++;
                }
            } catch (IOException e) {
                log.warn("Failed to copy demo logo for {}: {}", team.getShortName(), e.getMessage());
            }
        }
        log.info("Demo logos copied for {}/{} teams", copied, allTeams.size());
    }

    private Season createSeason(String name, int year, int number, String description, ScoringDefaults scorings) {
        var season = new Season(name, year, number);
        season.setDescription(description);
        season.setRaceScoring(scorings.raceScoring());
        season.setMatchScoring(scorings.matchScoring());
        return season;
    }

    private void seedDrivers() {
        // VRX — Velocity Racing (10 drivers)
        driver("VRX_Driver01", "Marco Ferretti");
        driver("VRX_Driver02", "Sophie Laurent");
        driver("VRX_Driver03", "Kenji Nakamura");
        driver("VRX_Driver04", "Elena Vasquez");
        driver("VRX_Driver05", "Luca Bianchi");
        driver("VRX_Driver06", "Anya Kowalski");
        driver("VRX_Driver07", "Omar Khalid");
        driver("VRX_Driver08", "Clara Dubois");
        driver("VRX_Driver09", "Raj Patel");
        driver("VRX_Driver10", "Ingrid Holm");

        // SGM — Shadow Grid Motorsport (10 drivers)
        driver("SGM_Driver01", "Viktor Sorokin");
        driver("SGM_Driver02", "Mei Lin Chen");
        driver("SGM_Driver03", "Callum Briggs");
        driver("SGM_Driver04", "Fatima Al-Hassan");
        driver("SGM_Driver05", "Diego Reyes");
        driver("SGM_Driver06", "Petra Novak");
        driver("SGM_Driver07", "Tomas Havel");
        driver("SGM_Driver08", "Amara Osei");
        driver("SGM_Driver09", "Finn Larsen");
        driver("SGM_Driver10", "Yuki Tanaka");

        // ADR — Apex Drift Racing (10 drivers)
        driver("ADR_Driver01", "Carlos Montoya");
        driver("ADR_Driver02", "Aisha Nwosu");
        driver("ADR_Driver03", "Henrik Bergstrom");
        driver("ADR_Driver04", "Priya Sharma");
        driver("ADR_Driver05", "Jack O'Brien");
        driver("ADR_Driver06", "Nadia Popescu");
        driver("ADR_Driver07", "Sven Eriksson");
        driver("ADR_Driver08", "Laila Mansour");
        driver("ADR_Driver09", "Bruno Costa");
        driver("ADR_Driver10", "Hana Suzuki");

        // TBR — Thunderbolt Raceworks (10 drivers)
        driver("TBR_Driver01", "Ryan MacLeod");
        driver("TBR_Driver02", "Zoe Fischer");
        driver("TBR_Driver03", "Ivan Petrov");
        driver("TBR_Driver04", "Amelia Torres");
        driver("TBR_Driver05", "Leo Hoffmann");
        driver("TBR_Driver06", "Nour El-Din");
        driver("TBR_Driver07", "Grace Kim");
        driver("TBR_Driver08", "Matteo Romano");
        driver("TBR_Driver09", "Sonia Blanc");
        driver("TBR_Driver10", "Arjun Mehta");

        // ICL — Iron Circuit League (10 drivers)
        driver("ICL_Driver01", "Patrick Leclaire");
        driver("ICL_Driver02", "Yuna Park");
        driver("ICL_Driver03", "Thomas Mueller");
        driver("ICL_Driver04", "Isabela Carvalho");
        driver("ICL_Driver05", "Alexei Volkov");
        driver("ICL_Driver06", "Chloe Martin");
        driver("ICL_Driver07", "Darius Okafor");
        driver("ICL_Driver08", "Miriam Steinberg");
        driver("ICL_Driver09", "Hamid Rezai");
        driver("ICL_Driver10", "Valentina Russo");

        // SVT — Stellar Velocity Team (10 drivers)
        driver("SVT_Driver01", "Emre Demir");
        driver("SVT_Driver02", "Lin Xiaoming");
        driver("SVT_Driver03", "Astrid Johansen");
        driver("SVT_Driver04", "Rafael Ortega");
        driver("SVT_Driver05", "Nia Williams");
        driver("SVT_Driver06", "Stefan Kovar");
        driver("SVT_Driver07", "Yuki Hashimoto");
        driver("SVT_Driver08", "Bianca Ferrari");
        driver("SVT_Driver09", "Kofi Mensah");
        driver("SVT_Driver10", "Vera Kuznetsova");

        // NFR — Nitro Forge Racing (10 drivers)
        driver("NFR_Driver01", "Jake Morrison");
        driver("NFR_Driver02", "Sakura Ito");
        driver("NFR_Driver03", "Pierre Lefebvre");
        driver("NFR_Driver04", "Amina Diallo");
        driver("NFR_Driver05", "Ben Hartley");
        driver("NFR_Driver06", "Lara Ivanova");
        driver("NFR_Driver07", "Marcus Webb");
        driver("NFR_Driver08", "Chiara Esposito");
        driver("NFR_Driver09", "Daichi Watanabe");
        driver("NFR_Driver10", "Olivia Grant");

        // EGP — Eclipse Grand Prix (10 drivers)
        driver("EGP_Driver01", "Ravi Krishnan");
        driver("EGP_Driver02", "Anna Lindqvist");
        driver("EGP_Driver03", "Fabio Conti");
        driver("EGP_Driver04", "Yasmin El-Amin");
        driver("EGP_Driver05", "Connor Walsh");
        driver("EGP_Driver06", "Mia Johansson");
        driver("EGP_Driver07", "Andrei Moldovan");
        driver("EGP_Driver08", "Kayla Thompson");
        driver("EGP_Driver09", "Eduardo Lima");
        driver("EGP_Driver10", "Fiona Campbell");

        // HMS — Horizon Motorsport (10 drivers)
        driver("HMS_Driver01", "Nicolas Bernard");
        driver("HMS_Driver02", "Yuna Choi");
        driver("HMS_Driver03", "Alex Turner");
        driver("HMS_Driver04", "Rosa Martinez");
        driver("HMS_Driver05", "Dmitri Volkov");
        driver("HMS_Driver06", "Celine Mercier");
        driver("HMS_Driver07", "Tariq Hassan");
        driver("HMS_Driver08", "Elsa Bergman");
        driver("HMS_Driver09", "Michael Chen");
        driver("HMS_Driver10", "Lucia Moreno");

        // PWR — Pulse Wave Racing (10 drivers)
        driver("PWR_Driver01", "Sam Nguyen");
        driver("PWR_Driver02", "Ingrid Svensson");
        driver("PWR_Driver03", "Roberto Mancini");
        driver("PWR_Driver04", "Zara Ahmed");
        driver("PWR_Driver05", "Kevin O'Connor");
        driver("PWR_Driver06", "Nadia Florescu");
        driver("PWR_Driver07", "Hiroshi Yamamoto");
        driver("PWR_Driver08", "Camille Rousseau");
        driver("PWR_Driver09", "Tobias Keller");
        driver("PWR_Driver10", "Alicia Santos");
    }

    private void seedAliases() {
        var allDrivers = driverRepository.findAll();

        java.util.function.Function<String, org.ctc.domain.model.Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

        // Typical PSN ID changes — using fictive drivers
        var vrx01 = findDriver.apply("VRX_Driver01");
        vrx01.addAlias("VRX_OldPSN01");
        driverRepository.save(vrx01);

        var sgm03 = findDriver.apply("SGM_Driver03");
        sgm03.addAlias("SGM_CallumOld");
        sgm03.addAlias("SGM_CBriggs");
        driverRepository.save(sgm03);

        var adr05 = findDriver.apply("ADR_Driver05");
        adr05.addAlias("ADR_JackOB_v1");
        driverRepository.save(adr05);
    }

    private void seedSeasonDrivers() {
        var allTeams = teamRepository.findAll();
        var allDrivers = driverRepository.findAll();
        var allSeasons = seasonRepository.findAll();

        java.util.function.Function<String, Team> findParent = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        java.util.function.Function<String, Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

        java.util.function.Function<Integer, Season> findSeason = year ->
                allSeasons.stream()
                        .filter(s -> s.getYear() == year && s.getNumber() == 4)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Season", year));

        // Season 4 - 2026: assign all 10 drivers per team
        var s4 = findSeason.apply(2026);

        for (String psnId : List.of("VRX_Driver01", "VRX_Driver02", "VRX_Driver03", "VRX_Driver04",
                "VRX_Driver05", "VRX_Driver06", "VRX_Driver07", "VRX_Driver08",
                "VRX_Driver09", "VRX_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("VRX")));
        }
        for (String psnId : List.of("SGM_Driver01", "SGM_Driver02", "SGM_Driver03", "SGM_Driver04",
                "SGM_Driver05", "SGM_Driver06", "SGM_Driver07", "SGM_Driver08",
                "SGM_Driver09", "SGM_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("SGM")));
        }
        for (String psnId : List.of("ADR_Driver01", "ADR_Driver02", "ADR_Driver03", "ADR_Driver04",
                "ADR_Driver05", "ADR_Driver06", "ADR_Driver07", "ADR_Driver08",
                "ADR_Driver09", "ADR_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("ADR")));
        }
        for (String psnId : List.of("TBR_Driver01", "TBR_Driver02", "TBR_Driver03", "TBR_Driver04",
                "TBR_Driver05", "TBR_Driver06", "TBR_Driver07", "TBR_Driver08",
                "TBR_Driver09", "TBR_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("TBR")));
        }
        for (String psnId : List.of("ICL_Driver01", "ICL_Driver02", "ICL_Driver03", "ICL_Driver04",
                "ICL_Driver05", "ICL_Driver06", "ICL_Driver07", "ICL_Driver08",
                "ICL_Driver09", "ICL_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("ICL")));
        }
        for (String psnId : List.of("SVT_Driver01", "SVT_Driver02", "SVT_Driver03", "SVT_Driver04",
                "SVT_Driver05", "SVT_Driver06", "SVT_Driver07", "SVT_Driver08",
                "SVT_Driver09", "SVT_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("SVT")));
        }
        for (String psnId : List.of("NFR_Driver01", "NFR_Driver02", "NFR_Driver03", "NFR_Driver04",
                "NFR_Driver05", "NFR_Driver06", "NFR_Driver07", "NFR_Driver08",
                "NFR_Driver09", "NFR_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("NFR")));
        }
        for (String psnId : List.of("EGP_Driver01", "EGP_Driver02", "EGP_Driver03", "EGP_Driver04",
                "EGP_Driver05", "EGP_Driver06", "EGP_Driver07", "EGP_Driver08",
                "EGP_Driver09", "EGP_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("EGP")));
        }
        for (String psnId : List.of("HMS_Driver01", "HMS_Driver02", "HMS_Driver03", "HMS_Driver04",
                "HMS_Driver05", "HMS_Driver06", "HMS_Driver07", "HMS_Driver08",
                "HMS_Driver09", "HMS_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("HMS")));
        }
        for (String psnId : List.of("PWR_Driver01", "PWR_Driver02", "PWR_Driver03", "PWR_Driver04",
                "PWR_Driver05", "PWR_Driver06", "PWR_Driver07", "PWR_Driver08",
                "PWR_Driver09", "PWR_Driver10")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("PWR")));
        }

        log.info("Created season-driver assignments: s4={}",
                seasonDriverRepository.findBySeasonId(s4.getId()).size());
    }

    private void seedTeamCards(Season activeSeason) {
        try {
            var paths = teamCardService.generateAllCards(activeSeason);
            log.info("Generated {} team cards for active season", paths.size());
        } catch (Exception e) {
            log.warn("Team card generation skipped (Playwright not installed?): {}", e.getMessage());
        }
    }

    private void seedRaceLineups() {
        var scorings = new ScoringDefaults(
                raceScoringRepository.findAll().getFirst(),
                matchScoringRepository.findAll().getFirst());

        // === Completely isolated test data (no relation to real teams/drivers) ===

        // Test teams
        var testAlpha = teamRepository.save(new Team("Test Alpha Racing", "T-ALF"));
        var testBravo = teamRepository.save(new Team("Test Bravo Racing", "T-BRV"));
        var testBravo1 = teamRepository.save(new Team("Test Bravo Racing 1", "T-BRV 1", testBravo));
        var testBravo2 = teamRepository.save(new Team("Test Bravo Racing 2", "T-BRV 2", testBravo));

        // Test drivers
        var tda1 = driver("Test_Alpha_1", "Test Alpha Driver 1");
        var tda2 = driver("Test_Alpha_2", "Test Alpha Driver 2");
        var tdb1 = driver("Test_Bravo1_1", "Test Bravo1 Driver 1");
        var tdb2 = driver("Test_Bravo1_2", "Test Bravo1 Driver 2");
        var tdb3 = driver("Test_Bravo2_1", "Test Bravo2 Driver 1");
        var tdb4 = driver("Test_Bravo2_2", "Test Bravo2 Driver 2");

        // Test-Season 2026: T-ALF vs T-BRV 1, T-ALF vs T-BRV 2
        var testSeason1 = createSeason("Test-Season 2026", 2026, 99, "Test", scorings);
        List.of(testAlpha, testBravo, testBravo1, testBravo2).forEach(testSeason1::addTeam);
        seasonRepository.save(testSeason1);

        var md1 = matchdayRepository.save(new Matchday(testSeason1, "Test MD 1", 1));

        var match1 = new Match();
        match1.setMatchday(md1);
        match1.setHomeTeam(testAlpha);
        match1.setAwayTeam(testBravo1);
        matchRepository.save(match1);
        var race1 = new Race();
        race1.setMatchday(md1);
        race1.setMatch(match1);
        race1.setSettings(createTestSettings(race1));
        raceRepository.save(race1);
        raceLineupRepository.save(new RaceLineup(race1, tda1, testAlpha));
        raceLineupRepository.save(new RaceLineup(race1, tda2, testAlpha));
        raceLineupRepository.save(new RaceLineup(race1, tdb1, testBravo1));
        raceLineupRepository.save(new RaceLineup(race1, tdb2, testBravo1));

        var match2 = new Match();
        match2.setMatchday(md1);
        match2.setHomeTeam(testAlpha);
        match2.setAwayTeam(testBravo2);
        matchRepository.save(match2);
        var race2 = new Race();
        race2.setMatchday(md1);
        race2.setMatch(match2);
        race2.setSettings(createTestSettings(race2));
        raceRepository.save(race2);
        raceLineupRepository.save(new RaceLineup(race2, tda1, testAlpha));
        raceLineupRepository.save(new RaceLineup(race2, tda2, testAlpha));
        raceLineupRepository.save(new RaceLineup(race2, tdb3, testBravo2));
        raceLineupRepository.save(new RaceLineup(race2, tdb4, testBravo2));

        // Test-Season 2025: T-ALF vs T-BRV (multi-season test)
        var testSeason2 = createSeason("Test-Season 2025", 2025, 98, "Test", scorings);
        List.of(testAlpha, testBravo).forEach(testSeason2::addTeam);
        seasonRepository.save(testSeason2);

        var md2 = matchdayRepository.save(new Matchday(testSeason2, "Test MD 1", 1));
        var match3 = new Match();
        match3.setMatchday(md2);
        match3.setHomeTeam(testAlpha);
        match3.setAwayTeam(testBravo);
        matchRepository.save(match3);
        var race3 = new Race();
        race3.setMatchday(md2);
        race3.setMatch(match3);
        raceRepository.save(race3);
        raceLineupRepository.save(new RaceLineup(race3, tda1, testAlpha));
        raceLineupRepository.save(new RaceLineup(race3, tda2, testAlpha));

        log.info("Created test data: {} test-teams, {} test-drivers, {} races, {} lineups",
                4, 6, raceRepository.count(), raceLineupRepository.count());
    }

    private Driver driver(String psnId, String nickname) {
        return driverRepository.save(new Driver(psnId, nickname));
    }

    private RaceSettings createTestSettings(Race race) {
        var settings = new RaceSettings(race);
        settings.setNumberOfLaps(20);
        settings.setTyreWearMultiplier(3);
        settings.setFuelConsumptionMultiplier(3);
        settings.setRefuelingSpeed(10);
        settings.setInitialFuel("90");
        settings.setNumberOfRequiredPitStops(0);
        settings.setTimeProgressionMultiplier(5);
        settings.setWeather("Preset S02");
        settings.setTimeOfDay("Afternoon");
        settings.setAvailableTyres("RS, RM, RH, I, W");
        settings.setMandatoryTyres("RS, RM, RH");
        return settings;
    }
}
