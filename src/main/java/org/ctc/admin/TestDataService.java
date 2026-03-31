package org.ctc.admin;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataService {

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
        seedSeasons(teams, scorings);
        seedDrivers();
        seedSeasonDrivers();
        seedRaceLineups();
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
                team("Project One Racing", "P1R", "#5ea6f1", "#f5170a", "#FFFFFF"),
                team("Community League Racing", "CLR", "#0467f5", "#000000", "#FFFFFF"),
                team("Tidgney Community Racing", "TCR", "#ffff04", "#fb0214", "#000000"),
                team("Amigos Racing Team", "ART", "#ffff04", "#0000ff", "#FFFFFF"),
                team("Apex Hunter Racing", "AHR", "#ff0101", "#000000", "#FFFFFF"),
                team("Medway Racing League", "MRL", "#000000", "#FFFFFF", "#333333"),
                team("Gen-X Racing", "GXR", "#f78000", "#000000", "#FFFFFF"),
                team("Dream Team Racing", "DTR", "#b00001", "#101010", "#FFFFFF"),
                team("VEZ Racing Team", "VEZ", "#ff66c4", "#FFFFFF", "#000000"),
                team("The Neutrals Racing", "TNR", "#016c88", "#b50001", "#000000")
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
        var clr = teams.stream().filter(t -> t.getShortName().equals("CLR")).findFirst().orElseThrow();
        teamRepository.save(subTeam("Community League Racing 1", "CLR 1", clr, "#0467f5", "#000000", "#FFFFFF"));
        teamRepository.save(subTeam("Community League Racing 2", "CLR 2", clr, "#0467f5", "#FFFFFF", "#000000"));

        var tnr = teams.stream().filter(t -> t.getShortName().equals("TNR")).findFirst().orElseThrow();
        teamRepository.save(subTeam("The Neutrals Racing A", "TNR A", tnr, "#0281a3", "#FFFFFF", "#a60100"));
        teamRepository.save(subTeam("The Neutrals Racing B", "TNR B", tnr, "#ba0001", "#FFFFFF", "#067392"));
        teamRepository.save(subTeam("The Neutrals Racing C", "TNR C", tnr, "#FFFFFF", "#039bc3", "#d70200"));

        var ahr = teams.stream().filter(t -> t.getShortName().equals("AHR")).findFirst().orElseThrow();
        teamRepository.save(subTeam("Apex Hunter Racing 1", "AHR 1", ahr, "#ff0101", "#000000", "#FFFFFF"));
        teamRepository.save(subTeam("Apex Hunter Racing 2", "AHR 2", ahr, "#ff0101", "#FFFFFF", "#000000"));

        var p1r = teams.stream().filter(t -> t.getShortName().equals("P1R")).findFirst().orElseThrow();
        teamRepository.save(subTeam("Project One Racing X", "P1Rx", p1r));
        teamRepository.save(subTeam("Project One Racing", "P1R", p1r));

        log.info("Created sub-teams: CLR(2), TNR(3), AHR(2), P1R(2)");
    }

    private void seedSeasons(List<Team> parentTeams, ScoringDefaults scorings) {
        var allTeams = teamRepository.findAll();

        // Helper to find parent team (no parent) by shortName
        java.util.function.Function<String, Team> findParent = (shortName) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Parent team not found: " + shortName));

        // Helper to find sub-team by shortName (has parent)
        java.util.function.Function<String, Team> findSub = (shortName) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Sub-team not found: " + shortName));

        // Older seasons: all parent teams
        for (var entry : List.of(
                new Object[]{"Season 1 - 2023 - Group A", 2023, 1, "Group A"},
                new Object[]{"Season 1 - 2023 - Group B", 2023, 1, "Group B"},
                new Object[]{"Season 2 - 2024", 2024, 2, null})) {
            var season = createSeason((String) entry[0], (int) entry[1], (int) entry[2], (String) entry[3], scorings);
            parentTeams.forEach(season::addTeam);
            seasonRepository.save(season);
        }

        // Season 3 - 2025 - Group A: P1Rx, CLR, MRL, TCR, GXR
        var s3a = createSeason("Season 3 - 2025 - Group A", 2025, 3, "Group A", scorings);
        List.of(
                findSub.apply("P1Rx"),
                findParent.apply("CLR"),
                findParent.apply("MRL"),
                findParent.apply("TCR"),
                findParent.apply("GXR")
        ).forEach(s3a::addTeam);
        seasonRepository.save(s3a);

        // Season 3 - 2025 - Group B: P1R parent + P1R sub-team, AHR, DTR, ART
        var s3b = createSeason("Season 3 - 2025 - Group B", 2025, 3, "Group B", scorings);
        List.of(
                findParent.apply("P1R"),
                findSub.apply("P1R"),
                findParent.apply("AHR"),
                findParent.apply("DTR"),
                findParent.apply("ART")
        ).forEach(s3b::addTeam);
        seasonRepository.save(s3b);

        // Season 4 - 2026: all parents with subs + standalone parents
        var s4 = createSeason("Season 4 - 2026", 2026, 4, null, scorings);
        s4.setActive(true);
        List.of(
                findParent.apply("CLR"),
                findSub.apply("CLR 1"),
                findSub.apply("CLR 2"),
                findParent.apply("TNR"),
                findSub.apply("TNR A"),
                findSub.apply("TNR B"),
                findSub.apply("TNR C"),
                findParent.apply("P1R"),
                findParent.apply("DTR"),
                findParent.apply("MRL"),
                findParent.apply("ART"),
                findParent.apply("AHR"),
                findSub.apply("AHR 1"),
                findSub.apply("AHR 2"),
                findParent.apply("VEZ"),
                findParent.apply("GXR"),
                findParent.apply("TCR")
        ).forEach(s4::addTeam);
        seasonRepository.save(s4);

        // Set ratings for active season
        s4.findSeasonTeam(findSub.apply("CLR 1")).ifPresent(st -> st.setRating(92));
        s4.findSeasonTeam(findSub.apply("CLR 2")).ifPresent(st -> st.setRating(87));
        s4.findSeasonTeam(findSub.apply("TNR A")).ifPresent(st -> st.setRating(93));
        s4.findSeasonTeam(findSub.apply("TNR B")).ifPresent(st -> st.setRating(85));
        s4.findSeasonTeam(findSub.apply("TNR C")).ifPresent(st -> st.setRating(85));
        s4.findSeasonTeam(findParent.apply("P1R")).ifPresent(st -> st.setRating(93));
        s4.findSeasonTeam(findParent.apply("DTR")).ifPresent(st -> st.setRating(85));
        s4.findSeasonTeam(findParent.apply("MRL")).ifPresent(st -> {
            st.setRating(84);
            st.setPrimaryColor("#1116aa");
            st.setAccentColor("#134f7c");
        });
        s4.findSeasonTeam(findParent.apply("ART")).ifPresent(st -> st.setRating(87));
        s4.findSeasonTeam(findSub.apply("AHR 1")).ifPresent(st -> st.setRating(92));
        s4.findSeasonTeam(findSub.apply("AHR 2")).ifPresent(st -> st.setRating(88));
        s4.findSeasonTeam(findParent.apply("VEZ")).ifPresent(st -> st.setRating(88));
        s4.findSeasonTeam(findParent.apply("GXR")).ifPresent(st -> st.setRating(83));
        s4.findSeasonTeam(findParent.apply("TCR")).ifPresent(st -> st.setRating(86));
        seasonRepository.save(s4);
    }

    private void copyDemoLogos(List<Team> parentTeams) {
        var allTeams = teamRepository.findAll();
        Path uploadBase = Paths.get("uploads/teams").toAbsolutePath().normalize();
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
                }
            } catch (IOException e) {
                log.warn("Failed to copy demo logo for {}: {}", team.getShortName(), e.getMessage());
            }
        }
        log.info("Demo logos copied for {} teams", allTeams.size());
    }

    private Season createSeason(String name, int year, int number, String description, ScoringDefaults scorings) {
        var season = new Season(name, year, number);
        season.setDescription(description);
        season.setRaceScoring(scorings.raceScoring());
        season.setMatchScoring(scorings.matchScoring());
        return season;
    }

    private void seedDrivers() {
        // P1R
        driver("France-k88", "P1R_Francek88");
        driver("P1R_Jake", "P1R_Seagull");
        driver("P1R_OldBanger", "P1R_OldBanger");
        driver("P1R_SLAMMER", "S L \u039b \u039c \u039c \u039e R");
        driver("Unfazed__be", "P1R_Unfazed_BE");
        driver("P1R_Valkyrie", "P1R_Valkyrie");
        driver("motorstormhero", "P1R_Motorstorm");
        driver("YT_Sorte13", "P1R_Sorte13");

        // CLR
        driver("BetelgeuzeFIN", "J. Itkonen");
        driver("chiccoblasi", "E. Blasi");
        driver("CLR_Prodigy_97", "Prodigy_97");
        driver("CLR_RichyI78", "TCR_RichyI78");
        driver("CSX_Thomas", "CSX_Thomas");
        driver("DylanCliff_28", "D. Clifford");
        driver("IEquinoXe-", "EquinoXe");
        driver("kurt_666_", "Major Guinness");
        driver("lemonysqueez", "Lemony Squeez");
        driver("RA_F1nalized__", "F1nalized");
        driver("RA_Shred", "Shred");
        driver("RA_Yannis73", "Sir Yancelot");
        driver("RiverRuckus", "CLR_RiverRuckus");
        driver("Slugzy_88", "Slugsy");

        // TCR
        driver("Etlits", "TCR_Arumes");
        driver("Hogston_GT", "HogstonGT");
        driver("TCR_Bracing1", "TCR_Bracing1");
        driver("TCR_Rapid_GT", "TCR_Rapid_GT");
        driver("TCR_Sheltie", "TCR_Sheltie");
        driver("TCR_Sonic", "TCR_Sonic");
        driver("TCR_Tidgney", "TCR_Tidgney");
        driver("TCR_White-tiger", "WhiteTiger");
        driver("bmataz", "TCR_Tazz");
        driver("YtrRytonlad28", "Tcr-grt-rytonlad");

        // ART
        driver("ART_Lango666", "ART_Lango");
        driver("beardiemcbeard", "ART_Beardie");
        driver("CJMR53", "ART_CJMR");
        driver("eRA_mikebrfc", "eRA_Judas");
        driver("ginnerquinny61", "ART_Quinny");
        driver("kylegamesdrums", "ART_MrKyle");
        driver("Matt2_3_7", "ART_Matt2_3_7");
        driver("RA_Tobi", "RA-A_Tobi");

        // AHR
        driver("AHR_Hills_93", "AHR_Hills_93");
        driver("AHR_j_mac", "AHR j mac");
        driver("AHR-PezzzaGT", "AHR-PezzzaGT");
        driver("AHR-Tankbro", "AHR Tankbro-_-");
        driver("danfn22016", "Danfn22016");
        driver("grey_roc", "Grey_roc GT");
        driver("Jacko_GT7", "AHR-Jacko");
        driver("JackPlayz_01", "JKPZ01");
        driver("Lemonz7836", "Lemon87\u00b9");
        driver("miggldeehiggins", "Micky D Higgins");
        driver("OFFICIAL_001", "AHR_REDACTED");
        driver("PnR-Proton", "AHR-Proton");
        driver("remir201", "AHR_I K O A");
        driver("Saittam-46", "Amateus46");
        driver("stevedp81", "AHR_Steviep");
        driver("stigimoss", "Stigimoss");
        driver("Tracer-tel", "TEL");

        // MRL
        driver("ApexMagnet", "ApexMagnet");
        driver("MRL_Bish", "MRL_MikeBish");
        driver("MRL_IrIsH_ToNy", "MRL_Greta^/");
        driver("MRL_JOHNNYWAFFLE", "MRL_JOHNNYWAFFLE");
        driver("MRL_Splinter117", "MRL_Splinter117");
        driver("Sparkzmajor", "MRL Sparkzmajor");

        // GXR
        driver("Gen-X_Dan98", "Gen-X_Dan98");
        driver("Gen-X_Darlobhoy", "Gen-X_Darlobhoy");
        driver("Gen-X_JWrenchy", "Gen-X_JWrenchy");
        driver("Gen-X_MynameJeff", "Gen-X_MynameJeff");
        driver("Gen-X_OldFart", "TNT_OLDFART");
        driver("Gen-X_Sainana", "B. Silva");
        driver("Gen-X_Sissy", "C. Howell");
        driver("Gen-X_KMaru", "JJ");
        driver("Gen-X_Wicksy", "Gen-X_Wicksy");

        // DTR
        driver("DTR_Butzen-Katz", "DTR B\u00fctzen-Katz");
        driver("DTR_H1PPYH33D", "DTR HippyHeed");
        driver("DTR_Kierin", "DTR Kierin");
        driver("DTR_M3guy", "DTR M3Guy");
        driver("DTR_MoominPappa", "DTR Moomin");
        driver("DTR_Rosdwerg", "DTR_Rosdwerg");
        driver("is250dec", "DTR_DEC");
        driver("Jaristoteles", "DTR Jari");
        driver("mugelina", "DTR_mugelina");
        driver("Sionetica", "Sio");

        // VEZ
        driver("andreahoppus", "AndreaHoppus");
        driver("FeArToMa1295", "Feartoma95");
        driver("freshciccio01", "VRT Flexciccio");
        driver("Gnuccaria", "N. Blasi");
        driver("InuyashaGodYokai", "InuyashaGodYokai");
        driver("Sonny061288", "SonnyStyle");
        driver("VRT_Incredibile", "G.Pancaldi");
        driver("VRT_Pastinacalda", "G. Mantineo");

        // TNR
        driver("Chaz__CA", "TNR_Chaz");
        driver("D-man371D-man", "TNR_D-Man");
        driver("Deekuhn", "TNR_Deaky");
        driver("Dirty_Donavan", "TNR_SimDudeSA");
        driver("Fjneet90", "TNR_FJ");
        driver("Ghostriderz16173", "TNR_Ghostrider16");
        driver("GMZ_Alfred", "TNR_Alfred");
        driver("LEVITIUS", "TNR_LEVITIUS");
        driver("Lightning_Lorry", "TNR_Lawrence");
        driver("LotariRacing", "TNR_Lotari");
        driver("Mo_Flavor", "TNR_Mo Flavor");
        driver("Nutcap_1", "TNR_Nutcap");
        driver("panicpotato17", "TNR_panicpotato");
        driver("Phantom_Steve111", "TNR_Phantom");
        driver("RayCarter", "TNR_RayCarter");
        driver("Savvy-Unchained", "TNR_SAVVY");
        driver("sir_maggs", "TNR_sir-maggs");
        driver("TNR_Capt_Slow", "TNR_Capt_Slow");
        driver("TNR_SHAWN46", "TNR_SHAWN46");
        driver("TNR_Wipperman537", "TNR_Wipperman");
        driver("VIVSRC370", "TNR_SRC_VIV");
    }

    private void seedSeasonDrivers() {
        var allTeams = teamRepository.findAll();
        var allDrivers = driverRepository.findAll();
        var allSeasons = seasonRepository.findAll();

        java.util.function.Function<String, Team> findParent = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Parent team not found: " + shortName));

        java.util.function.Function<String, Team> findSub = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Sub-team not found: " + shortName));

        java.util.function.Function<String, Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Driver not found: " + psnId));

        java.util.function.Function<String, Season> findSeason = name ->
                allSeasons.stream()
                        .filter(s -> s.getName().equals(name))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Season not found: " + name));

        // Season 4 - 2026
        var s4 = findSeason.apply("Season 4 - 2026");

        for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger",
                "YT_Sorte13", "Unfazed__be", "P1R_Valkyrie", "motorstormhero")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("P1R")));
        }
        // Sub-Team-Zuordnungen werden NICHT geseeded — die kommen aus dem Import
        // (ensureSeasonDriver aktualisiert das Team bei erneutem Import)
        // Hier nur Parent-Team-Zuordnungen als Platzhalter fuer Entwicklung ohne Import
        for (String psnId : List.of("BetelgeuzeFIN", "chiccoblasi", "CLR_Prodigy_97",
                "CLR_RichyI78", "CSX_Thomas", "DylanCliff_28",
                "IEquinoXe-", "kurt_666_", "lemonysqueez",
                "RA_F1nalized__", "RA_Shred", "RA_Yannis73")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("CLR")));
        }
        for (String psnId : List.of("Chaz__CA", "D-man371D-man", "Deekuhn",
                "Dirty_Donavan", "Fjneet90", "Ghostriderz16173", "GMZ_Alfred",
                "LEVITIUS", "Lightning_Lorry", "LotariRacing",
                "Mo_Flavor", "Nutcap_1", "panicpotato17", "Phantom_Steve111",
                "RayCarter", "Savvy-Unchained", "sir_maggs",
                "TNR_Capt_Slow", "TNR_SHAWN46", "TNR_Wipperman537")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("TNR")));
        }
        for (String psnId : List.of("AHR_Hills_93", "AHR_j_mac", "AHR-PezzzaGT",
                "AHR-Tankbro", "danfn22016", "grey_roc", "Jacko_GT7", "JackPlayz_01",
                "Lemonz7836", "miggldeehiggins", "OFFICIAL_001",
                "PnR-Proton", "remir201", "Saittam-46", "stevedp81", "stigimoss", "Tracer-tel")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("AHR")));
        }
        for (String psnId : List.of("TCR_Rapid_GT", "TCR_Sheltie", "TCR_Sonic", "TCR_Tidgney",
                "Etlits", "Hogston_GT", "TCR_Bracing1", "TCR_White-tiger", "bmataz", "YtrRytonlad28")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("TCR")));
        }
        for (String psnId : List.of("DTR_Butzen-Katz", "DTR_H1PPYH33D", "DTR_Kierin", "DTR_M3guy",
                "DTR_MoominPappa", "DTR_Rosdwerg", "is250dec", "Jaristoteles", "mugelina", "Sionetica")) {
            seasonDriverRepository.save(new SeasonDriver(s4, findDriver.apply(psnId), findParent.apply("DTR")));
        }

        // Season 3 - 2025 - Group A (multi-season testing)
        var s3a = findSeason.apply("Season 3 - 2025 - Group A");
        for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger")) {
            seasonDriverRepository.save(new SeasonDriver(s3a, findDriver.apply(psnId), findSub.apply("P1Rx")));
        }

        log.info("Created season-driver assignments: s4={}, s3a={}",
                seasonDriverRepository.findBySeasonId(s4.getId()).size(),
                seasonDriverRepository.findBySeasonId(s3a.getId()).size());
    }

    private void seedRaceLineups() {
        var scorings = new ScoringDefaults(
                raceScoringRepository.findAll().getFirst(),
                matchScoringRepository.findAll().getFirst());

        // === Komplett isolierte Testdaten (kein Bezug zu echten Teams/Fahrern) ===

        // Test-Teams
        var testAlpha = teamRepository.save(new Team("Test Alpha Racing", "T-ALF"));
        var testBravo = teamRepository.save(new Team("Test Bravo Racing", "T-BRV"));
        var testBravo1 = teamRepository.save(new Team("Test Bravo Racing 1", "T-BRV 1", testBravo));
        var testBravo2 = teamRepository.save(new Team("Test Bravo Racing 2", "T-BRV 2", testBravo));

        // Test-Fahrer
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
