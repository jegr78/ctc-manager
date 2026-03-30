package de.ctc.admin;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return teamRepository.saveAll(List.of(
                new Team("Project One Racing", "P1R"),
                new Team("Community League Racing", "CLR"),
                new Team("Tidgney Community Racing", "TCR"),
                new Team("Amigos Racing Team", "ART"),
                new Team("Apex Hunter Racing", "AHR"),
                new Team("Medway Racing League", "MRL"),
                new Team("Gen-X Racing", "GXR"),
                new Team("Dream Team Racing", "DTR"),
                new Team("VEZ Racing Team", "VEZ"),
                new Team("The Neutrals Racing", "TNR")
        ));
    }

    private void seedSubTeams(List<Team> teams) {
        var clr = teams.stream().filter(t -> t.getShortName().equals("CLR")).findFirst().orElseThrow();
        teamRepository.save(new Team("Community League Racing 1", "CLR 1", clr));
        teamRepository.save(new Team("Community League Racing 2", "CLR 2", clr));

        var tnr = teams.stream().filter(t -> t.getShortName().equals("TNR")).findFirst().orElseThrow();
        teamRepository.save(new Team("The Neutrals Racing A", "TNR A", tnr));
        teamRepository.save(new Team("The Neutrals Racing B", "TNR B", tnr));
        teamRepository.save(new Team("The Neutrals Racing C", "TNR C", tnr));

        var ahr = teams.stream().filter(t -> t.getShortName().equals("AHR")).findFirst().orElseThrow();
        teamRepository.save(new Team("Apex Hunter Racing 1", "AHR 1", ahr));
        teamRepository.save(new Team("Apex Hunter Racing 2", "AHR 2", ahr));

        var p1r = teams.stream().filter(t -> t.getShortName().equals("P1R")).findFirst().orElseThrow();
        teamRepository.save(new Team("Project One Racing X", "P1Rx", p1r));
        teamRepository.save(new Team("Project One Racing", "P1R", p1r));

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
        for (String name : List.of("Season 1 - 2023 - Group A", "Season 1 - 2023 - Group B", "Season 2 - 2024")) {
            var season = createSeason(name, scorings);
            season.getTeams().addAll(parentTeams);
            seasonRepository.save(season);
        }

        // Season 3 - 2025 - Group A: P1Rx, CLR, MRL, TCR, GXR
        var s3a = createSeason("Season 3 - 2025 - Group A", scorings);
        s3a.getTeams().addAll(List.of(
                findSub.apply("P1Rx"),
                findParent.apply("CLR"),
                findParent.apply("MRL"),
                findParent.apply("TCR"),
                findParent.apply("GXR")
        ));
        seasonRepository.save(s3a);

        // Season 3 - 2025 - Group B: P1R parent + P1R sub-team, AHR, DTR, ART
        var s3b = createSeason("Season 3 - 2025 - Group B", scorings);
        s3b.getTeams().addAll(List.of(
                findParent.apply("P1R"),
                findSub.apply("P1R"),
                findParent.apply("AHR"),
                findParent.apply("DTR"),
                findParent.apply("ART")
        ));
        seasonRepository.save(s3b);

        // Season 4 - 2026: all parents with subs + standalone parents
        var s4 = createSeason("Season 4 - 2026", scorings);
        s4.setActive(true);
        s4.getTeams().addAll(List.of(
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
        ));
        seasonRepository.save(s4);
    }

    private Season createSeason(String name, ScoringDefaults scorings) {
        var season = new Season(name);
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
        var allTeams = teamRepository.findAll();
        var allDrivers = driverRepository.findAll();

        java.util.function.Function<String, Team> findParent = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow();
        java.util.function.Function<String, Team> findSub = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow();
        java.util.function.Function<String, Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow();

        var s4 = seasonRepository.findByName("Season 4 - 2026").orElseThrow();
        var s3a = seasonRepository.findByName("Season 3 - 2025 - Group A").orElseThrow();

        // Season 4: P1R vs DTR (standalone teams)
        var md1 = matchdayRepository.save(new Matchday(s4, "MD 1", 1));
        var match1 = new Match();
        match1.setMatchday(md1);
        match1.setHomeTeam(findParent.apply("P1R"));
        match1.setAwayTeam(findParent.apply("DTR"));
        matchRepository.save(match1);
        var race1 = new Race();
        race1.setMatchday(md1);
        race1.setMatch(match1);
        raceRepository.save(race1);

        for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger",
                "YT_Sorte13", "Unfazed__be")) {
            raceLineupRepository.save(new RaceLineup(race1, findDriver.apply(psnId), findParent.apply("P1R")));
        }
        for (String psnId : List.of("DTR_Butzen-Katz", "DTR_H1PPYH33D", "DTR_Kierin",
                "DTR_M3guy", "DTR_MoominPappa", "DTR_Rosdwerg")) {
            raceLineupRepository.save(new RaceLineup(race1, findDriver.apply(psnId), findParent.apply("DTR")));
        }

        // Season 4: CLR 1 vs TNR A (sub-teams)
        var match2 = new Match();
        match2.setMatchday(md1);
        match2.setHomeTeam(findSub.apply("CLR 1"));
        match2.setAwayTeam(findSub.apply("TNR A"));
        matchRepository.save(match2);
        var race2 = new Race();
        race2.setMatchday(md1);
        race2.setMatch(match2);
        raceRepository.save(race2);

        for (String psnId : List.of("BetelgeuzeFIN", "chiccoblasi", "CLR_Prodigy_97",
                "CLR_RichyI78", "CSX_Thomas", "DylanCliff_28")) {
            raceLineupRepository.save(new RaceLineup(race2, findDriver.apply(psnId), findSub.apply("CLR 1")));
        }
        for (String psnId : List.of("Chaz__CA", "D-man371D-man", "Deekuhn",
                "Dirty_Donavan", "Fjneet90", "Ghostriderz16173")) {
            raceLineupRepository.save(new RaceLineup(race2, findDriver.apply(psnId), findSub.apply("TNR A")));
        }

        // Season 4: CLR 2 vs TNR B (sub-teams)
        var match3 = new Match();
        match3.setMatchday(md1);
        match3.setHomeTeam(findSub.apply("CLR 2"));
        match3.setAwayTeam(findSub.apply("TNR B"));
        matchRepository.save(match3);
        var race3 = new Race();
        race3.setMatchday(md1);
        race3.setMatch(match3);
        raceRepository.save(race3);

        for (String psnId : List.of("IEquinoXe-", "kurt_666_", "lemonysqueez",
                "RA_F1nalized__", "RA_Shred", "RA_Yannis73")) {
            raceLineupRepository.save(new RaceLineup(race3, findDriver.apply(psnId), findSub.apply("CLR 2")));
        }
        for (String psnId : List.of("LEVITIUS", "Lightning_Lorry", "LotariRacing",
                "Mo_Flavor", "Nutcap_1", "panicpotato17")) {
            raceLineupRepository.save(new RaceLineup(race3, findDriver.apply(psnId), findSub.apply("TNR B")));
        }

        // Season 3 Group A: P1Rx vs CLR (multi-season test)
        var md3a = matchdayRepository.save(new Matchday(s3a, "MD 1", 1));
        var match3a = new Match();
        match3a.setMatchday(md3a);
        match3a.setHomeTeam(findSub.apply("P1Rx"));
        match3a.setAwayTeam(findParent.apply("CLR"));
        matchRepository.save(match3a);
        var race3a = new Race();
        race3a.setMatchday(md3a);
        race3a.setMatch(match3a);
        raceRepository.save(race3a);

        for (String psnId : List.of("France-k88", "P1R_Jake", "P1R_SLAMMER", "P1R_OldBanger")) {
            raceLineupRepository.save(new RaceLineup(race3a, findDriver.apply(psnId), findSub.apply("P1Rx")));
        }

        log.info("Created race lineups for {} races", raceRepository.count());
    }

    private void driver(String psnId, String nickname) {
        driverRepository.save(new Driver(psnId, nickname));
    }
}
