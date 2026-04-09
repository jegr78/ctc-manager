package org.ctc.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.ScoringService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

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
    private final RaceResultRepository raceResultRepository;
    private final ScoringService scoringService;
    private final EntityManager entityManager;

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
        seedAliases();
        seedSeasonDrivers();
        seedMatchdaysAndResults();
        seedRaceLineups();
        log.info("Seed data created: {} teams, {} seasons, {} drivers, {} race-lineups, {} results",
                teamRepository.count(), seasonRepository.count(), driverRepository.count(),
                raceLineupRepository.count(), raceResultRepository.count());
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
        var clr = teams.stream().filter(t -> t.getShortName().equals("CLR")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "CLR"));
        teamRepository.save(subTeam("Community League Racing 1", "CLR 1", clr, "#0467f5", "#000000", "#FFFFFF"));
        teamRepository.save(subTeam("Community League Racing 2", "CLR 2", clr, "#0467f5", "#FFFFFF", "#000000"));

        var tnr = teams.stream().filter(t -> t.getShortName().equals("TNR")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "TNR"));
        teamRepository.save(subTeam("The Neutrals Racing A", "TNR A", tnr, "#0281a3", "#FFFFFF", "#a60100"));
        teamRepository.save(subTeam("The Neutrals Racing B", "TNR B", tnr, "#ba0001", "#FFFFFF", "#067392"));
        teamRepository.save(subTeam("The Neutrals Racing C", "TNR C", tnr, "#FFFFFF", "#039bc3", "#d70200"));

        var ahr = teams.stream().filter(t -> t.getShortName().equals("AHR")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "AHR"));
        teamRepository.save(subTeam("Apex Hunter Racing 1", "AHR 1", ahr, "#ff0101", "#000000", "#FFFFFF"));
        teamRepository.save(subTeam("Apex Hunter Racing 2", "AHR 2", ahr, "#ff0101", "#FFFFFF", "#000000"));

        var p1r = teams.stream().filter(t -> t.getShortName().equals("P1R")).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Team", "P1R"));
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
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        // Helper to find sub-team by shortName (has parent)
        java.util.function.Function<String, Team> findSub = (shortName) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        // S1 2023 Group A: Round Robin (6 teams, mix of parents and sub-teams) per D-01, D-03, D-08
        var s1a = createSeason("Group A", 2023, 1, "Group A, Regular Season", scorings);
        s1a.setFormat(SeasonFormat.ROUND_ROBIN);
        List.of(findParent.apply("P1R"), findParent.apply("TCR"), findParent.apply("ART"),
                findParent.apply("MRL"), findParent.apply("GXR"), findSub.apply("CLR 1"))
                .forEach(s1a::addTeam);
        seasonRepository.save(s1a);

        // S1 2023 Group B: Round Robin (6 teams, mix of parents and sub-teams) per D-01, D-03, D-08
        var s1b = createSeason("Group B", 2023, 1, "Group B, Regular Season", scorings);
        s1b.setFormat(SeasonFormat.ROUND_ROBIN);
        List.of(findParent.apply("DTR"), findParent.apply("VEZ"),
                findSub.apply("CLR 2"), findSub.apply("TNR A"), findSub.apply("TNR B"), findSub.apply("AHR 1"))
                .forEach(s1b::addTeam);
        seasonRepository.save(s1b);

        // S2 2024: Swiss format (10 parent teams only) per D-01, D-09
        var s2 = createSeason("Regular Season", 2024, 2, "Round Robin", scorings);
        s2.setFormat(SeasonFormat.SWISS);
        parentTeams.forEach(s2::addTeam);
        seasonRepository.save(s2);

        // Season 3 - 2025 - Group A: P1Rx, CLR, MRL, TCR, GXR
        var s3a = createSeason("Group A", 2025, 3, "Group A, Regular Season", scorings);
        List.of(
                findSub.apply("P1Rx"),
                findParent.apply("CLR"),
                findParent.apply("MRL"),
                findParent.apply("TCR"),
                findParent.apply("GXR")
        ).forEach(s3a::addTeam);
        seasonRepository.save(s3a);

        // Season 3 - 2025 - Group B: P1R parent + P1R sub-team, AHR, DTR, ART
        var s3b = createSeason("Group B", 2025, 3, "Group B, Regular Season", scorings);
        List.of(
                findParent.apply("P1R"),
                findSub.apply("P1R"),
                findParent.apply("AHR"),
                findParent.apply("DTR"),
                findParent.apply("ART")
        ).forEach(s3b::addTeam);
        seasonRepository.save(s3b);

        // Season 4 - 2026: 14 match teams (7 standalone parents + 7 sub-teams) per D-10
        // CLR, TNR, AHR parents do NOT participate as match teams
        var s4 = createSeason("Regular Season", 2026, 4, null, scorings);
        s4.setActive(true);
        s4.setFormat(SeasonFormat.LEAGUE);
        List.of(
                findSub.apply("CLR 1"),
                findSub.apply("CLR 2"),
                findSub.apply("TNR A"),
                findSub.apply("TNR B"),
                findSub.apply("TNR C"),
                findSub.apply("AHR 1"),
                findSub.apply("AHR 2"),
                findParent.apply("P1R"),
                findParent.apply("DTR"),
                findParent.apply("MRL"),
                findParent.apply("ART"),
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
        Path uploadBase = Paths.get(uploadDir, "teams").toAbsolutePath().normalize();
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

    private void seedAliases() {
        var allDrivers = driverRepository.findAll();

        java.util.function.Function<String, org.ctc.domain.model.Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

        // Typical PSN ID changes
        var jake = findDriver.apply("P1R_Jake");
        jake.addAlias("P1R_Jake_Old");
        driverRepository.save(jake);

        var kurt = findDriver.apply("kurt_666_");
        kurt.addAlias("kurt_old_psn");
        kurt.addAlias("KurtTheGamer");
        driverRepository.save(kurt);

        var richy = findDriver.apply("CLR_RichyI78");
        richy.addAlias("TCR_RichyI78_v1");
        driverRepository.save(richy);
    }

    private void seedSeasonDrivers() {
        var allTeams = teamRepository.findAll();
        var allDrivers = driverRepository.findAll();
        var allSeasons = seasonRepository.findAll();

        java.util.function.Function<String, Team> findParent = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        java.util.function.Function<String, Team> findSub = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        java.util.function.Function<String, Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

        java.util.function.Function<Integer, Season> findSeason = year ->
                allSeasons.stream()
                        .filter(s -> s.getYear() == year)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Season", year));

        // Season 4 - 2026
        var s4 = findSeason.apply(2026);

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

        // Helper to find season by year and name
        java.util.function.BiFunction<Integer, String, Season> findSeasonByName = (year, name) ->
                allSeasons.stream()
                        .filter(s -> s.getYear() == year && s.getName().equals(name))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Season", name));

        // S1 2023 Group A: 6 drivers per team (P1R, TCR, ART, MRL, GXR + CLR 1)
        var s1a = findSeasonByName.apply(2023, "Group A");
        assignSeasonDrivers(s1a, "P1R", List.of("France-k88", "P1R_Jake", "P1R_SLAMMER",
                "P1R_OldBanger", "YT_Sorte13", "Unfazed__be"), findParent, findDriver);
        assignSeasonDrivers(s1a, "TCR", List.of("TCR_Rapid_GT", "TCR_Sheltie", "TCR_Sonic",
                "TCR_Tidgney", "Etlits", "Hogston_GT"), findParent, findDriver);
        assignSeasonDrivers(s1a, "ART", List.of("ART_Lango666", "beardiemcbeard", "CJMR53",
                "eRA_mikebrfc", "ginnerquinny61", "kylegamesdrums"), findParent, findDriver);
        assignSeasonDrivers(s1a, "MRL", List.of("ApexMagnet", "MRL_Bish", "MRL_IrIsH_ToNy",
                "MRL_JOHNNYWAFFLE", "MRL_Splinter117", "Sparkzmajor"), findParent, findDriver);
        assignSeasonDrivers(s1a, "GXR", List.of("Gen-X_Dan98", "Gen-X_Darlobhoy", "Gen-X_JWrenchy",
                "Gen-X_MynameJeff", "Gen-X_OldFart", "Gen-X_Sainana"), findParent, findDriver);
        // CLR 1 sub-team: SeasonDriver uses parent team CLR
        assignSeasonDrivers(s1a, "CLR", List.of("BetelgeuzeFIN", "chiccoblasi", "CLR_Prodigy_97",
                "CLR_RichyI78", "CSX_Thomas", "DylanCliff_28"), findParent, findDriver);

        // S1 2023 Group B: 6 drivers per team (DTR, VEZ + CLR 2, TNR A, TNR B, AHR 1)
        var s1b = findSeasonByName.apply(2023, "Group B");
        assignSeasonDrivers(s1b, "DTR", List.of("DTR_Butzen-Katz", "DTR_H1PPYH33D", "DTR_Kierin",
                "DTR_M3guy", "DTR_MoominPappa", "DTR_Rosdwerg"), findParent, findDriver);
        assignSeasonDrivers(s1b, "VEZ", List.of("andreahoppus", "FeArToMa1295", "freshciccio01",
                "Gnuccaria", "InuyashaGodYokai", "Sonny061288"), findParent, findDriver);
        // CLR 2: different drivers than Group A, parent team CLR
        assignSeasonDrivers(s1b, "CLR", List.of("IEquinoXe-", "kurt_666_", "lemonysqueez",
                "RA_F1nalized__", "RA_Shred", "RA_Yannis73"), findParent, findDriver);
        // TNR A: parent team TNR
        assignSeasonDrivers(s1b, "TNR", List.of("Chaz__CA", "D-man371D-man", "Deekuhn",
                "Dirty_Donavan", "Fjneet90", "Ghostriderz16173"), findParent, findDriver);
        // TNR B: different drivers, parent team TNR
        assignSeasonDrivers(s1b, "TNR", List.of("GMZ_Alfred", "LEVITIUS", "Lightning_Lorry",
                "LotariRacing", "Mo_Flavor", "Nutcap_1"), findParent, findDriver);
        // AHR 1: parent team AHR
        assignSeasonDrivers(s1b, "AHR", List.of("AHR_Hills_93", "AHR_j_mac", "AHR-PezzzaGT",
                "AHR-Tankbro", "danfn22016", "grey_roc"), findParent, findDriver);

        // S2 2024: 10 parent teams, 6 drivers each
        var s2 = findSeasonByName.apply(2024, "Regular Season");
        assignSeasonDrivers(s2, "P1R", List.of("France-k88", "P1R_Jake", "P1R_SLAMMER",
                "P1R_OldBanger", "YT_Sorte13", "Unfazed__be"), findParent, findDriver);
        assignSeasonDrivers(s2, "CLR", List.of("BetelgeuzeFIN", "chiccoblasi", "CLR_Prodigy_97",
                "CLR_RichyI78", "CSX_Thomas", "DylanCliff_28"), findParent, findDriver);
        assignSeasonDrivers(s2, "TCR", List.of("TCR_Rapid_GT", "TCR_Sheltie", "TCR_Sonic",
                "TCR_Tidgney", "Etlits", "Hogston_GT"), findParent, findDriver);
        assignSeasonDrivers(s2, "ART", List.of("ART_Lango666", "beardiemcbeard", "CJMR53",
                "eRA_mikebrfc", "ginnerquinny61", "kylegamesdrums"), findParent, findDriver);
        assignSeasonDrivers(s2, "AHR", List.of("AHR_Hills_93", "AHR_j_mac", "AHR-PezzzaGT",
                "AHR-Tankbro", "danfn22016", "grey_roc"), findParent, findDriver);
        assignSeasonDrivers(s2, "MRL", List.of("ApexMagnet", "MRL_Bish", "MRL_IrIsH_ToNy",
                "MRL_JOHNNYWAFFLE", "MRL_Splinter117", "Sparkzmajor"), findParent, findDriver);
        assignSeasonDrivers(s2, "GXR", List.of("Gen-X_Dan98", "Gen-X_Darlobhoy", "Gen-X_JWrenchy",
                "Gen-X_MynameJeff", "Gen-X_OldFart", "Gen-X_Sainana"), findParent, findDriver);
        assignSeasonDrivers(s2, "DTR", List.of("DTR_Butzen-Katz", "DTR_H1PPYH33D", "DTR_Kierin",
                "DTR_M3guy", "DTR_MoominPappa", "DTR_Rosdwerg"), findParent, findDriver);
        assignSeasonDrivers(s2, "VEZ", List.of("andreahoppus", "FeArToMa1295", "freshciccio01",
                "Gnuccaria", "InuyashaGodYokai", "Sonny061288"), findParent, findDriver);
        assignSeasonDrivers(s2, "TNR", List.of("Chaz__CA", "D-man371D-man", "Deekuhn",
                "Dirty_Donavan", "Fjneet90", "Ghostriderz16173"), findParent, findDriver);

        log.info("Created season-driver assignments: s4={}, s1a={}, s1b={}, s2={}",
                seasonDriverRepository.findBySeasonId(s4.getId()).size(),
                seasonDriverRepository.findBySeasonId(s1a.getId()).size(),
                seasonDriverRepository.findBySeasonId(s1b.getId()).size(),
                seasonDriverRepository.findBySeasonId(s2.getId()).size());
    }

    private void assignSeasonDrivers(Season season, String teamShortName,
            List<String> driverPsnIds,
            java.util.function.Function<String, Team> teamFinder,
            java.util.function.Function<String, Driver> driverFinder) {
        var team = teamFinder.apply(teamShortName);
        for (String psnId : driverPsnIds) {
            seasonDriverRepository.save(new SeasonDriver(season, driverFinder.apply(psnId), team));
        }
    }

    private void seedMatchdaysAndResults() {
        var raceScoring = raceScoringRepository.findAll().getFirst();
        var allSeasons = seasonRepository.findAll();
        var allTeams = teamRepository.findAll();
        var allDrivers = driverRepository.findAll();

        // Helper lambdas
        java.util.function.Function<String, Team> findParent = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() == null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        java.util.function.Function<String, Team> findSub = shortName ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && t.getParentTeam() != null)
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Team", shortName));

        java.util.function.Function<String, Driver> findDriver = psnId ->
                allDrivers.stream()
                        .filter(d -> d.getPsnId().equals(psnId))
                        .findFirst().orElseThrow(() -> new EntityNotFoundException("Driver", psnId));

        // Season lookups
        var s4 = allSeasons.stream().filter(s -> s.getYear() == 2026 && s.getNumber() == 4).findFirst().orElseThrow();
        var s2 = allSeasons.stream().filter(s -> s.getYear() == 2024 && s.getNumber() == 2).findFirst().orElseThrow();
        var s1a = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group A")).findFirst().orElseThrow();
        var s1b = allSeasons.stream().filter(s -> s.getYear() == 2023 && s.getName().equals("Group B")).findFirst().orElseThrow();

        // Position rotation patterns (6 home + 6 away = positions 1-12)
        int[][] homePositions = {
                {1, 3, 5, 7, 9, 11},   // MD1: home wins
                {2, 4, 6, 8, 10, 12},  // MD2: away wins
                {1, 2, 5, 6, 9, 10},   // MD3: home wins (different spread)
                {3, 4, 7, 8, 11, 12},  // MD4: away wins
                {1, 4, 5, 8, 9, 12}    // MD5: close match
        };
        int[] awayPositions0 = {2, 4, 6, 8, 10, 12};
        int[] awayPositions1 = {1, 3, 5, 7, 9, 11};
        int[] awayPositions2 = {3, 4, 7, 8, 11, 12};
        int[] awayPositions3 = {1, 2, 5, 6, 9, 10};
        int[] awayPositions4 = {2, 3, 6, 7, 10, 11};
        int[][] awayPositions = {awayPositions0, awayPositions1, awayPositions2, awayPositions3, awayPositions4};
        int[] fastestLapPositions = {1, 2, 3, 4, 5}; // Rotates per matchday

        // Driver assignments per team (6 drivers each) - reuse SeasonDriver assignments
        // S4 League: 14 teams - 7 standalone parents + 7 sub-teams
        var s4TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
        s4TeamDrivers.put(findParent.apply("P1R"), new Driver[]{
                findDriver.apply("France-k88"), findDriver.apply("P1R_Jake"), findDriver.apply("P1R_SLAMMER"),
                findDriver.apply("P1R_OldBanger"), findDriver.apply("YT_Sorte13"), findDriver.apply("Unfazed__be")});
        s4TeamDrivers.put(findParent.apply("TCR"), new Driver[]{
                findDriver.apply("TCR_Rapid_GT"), findDriver.apply("TCR_Sheltie"), findDriver.apply("TCR_Sonic"),
                findDriver.apply("TCR_Tidgney"), findDriver.apply("Etlits"), findDriver.apply("Hogston_GT")});
        s4TeamDrivers.put(findParent.apply("ART"), new Driver[]{
                findDriver.apply("ART_Lango666"), findDriver.apply("beardiemcbeard"), findDriver.apply("CJMR53"),
                findDriver.apply("eRA_mikebrfc"), findDriver.apply("ginnerquinny61"), findDriver.apply("kylegamesdrums")});
        s4TeamDrivers.put(findParent.apply("MRL"), new Driver[]{
                findDriver.apply("ApexMagnet"), findDriver.apply("MRL_Bish"), findDriver.apply("MRL_IrIsH_ToNy"),
                findDriver.apply("MRL_JOHNNYWAFFLE"), findDriver.apply("MRL_Splinter117"), findDriver.apply("Sparkzmajor")});
        s4TeamDrivers.put(findParent.apply("GXR"), new Driver[]{
                findDriver.apply("Gen-X_Dan98"), findDriver.apply("Gen-X_Darlobhoy"), findDriver.apply("Gen-X_JWrenchy"),
                findDriver.apply("Gen-X_MynameJeff"), findDriver.apply("Gen-X_OldFart"), findDriver.apply("Gen-X_Sainana")});
        s4TeamDrivers.put(findParent.apply("DTR"), new Driver[]{
                findDriver.apply("DTR_Butzen-Katz"), findDriver.apply("DTR_H1PPYH33D"), findDriver.apply("DTR_Kierin"),
                findDriver.apply("DTR_M3guy"), findDriver.apply("DTR_MoominPappa"), findDriver.apply("DTR_Rosdwerg")});
        s4TeamDrivers.put(findParent.apply("VEZ"), new Driver[]{
                findDriver.apply("andreahoppus"), findDriver.apply("FeArToMa1295"), findDriver.apply("freshciccio01"),
                findDriver.apply("Gnuccaria"), findDriver.apply("InuyashaGodYokai"), findDriver.apply("Sonny061288")});
        // Sub-teams: use parent team's drivers
        s4TeamDrivers.put(findSub.apply("CLR 1"), new Driver[]{
                findDriver.apply("BetelgeuzeFIN"), findDriver.apply("chiccoblasi"), findDriver.apply("CLR_Prodigy_97"),
                findDriver.apply("CLR_RichyI78"), findDriver.apply("CSX_Thomas"), findDriver.apply("DylanCliff_28")});
        s4TeamDrivers.put(findSub.apply("CLR 2"), new Driver[]{
                findDriver.apply("IEquinoXe-"), findDriver.apply("kurt_666_"), findDriver.apply("lemonysqueez"),
                findDriver.apply("RA_F1nalized__"), findDriver.apply("RA_Shred"), findDriver.apply("RA_Yannis73")});
        s4TeamDrivers.put(findSub.apply("TNR A"), new Driver[]{
                findDriver.apply("Chaz__CA"), findDriver.apply("D-man371D-man"), findDriver.apply("Deekuhn"),
                findDriver.apply("Dirty_Donavan"), findDriver.apply("Fjneet90"), findDriver.apply("Ghostriderz16173")});
        s4TeamDrivers.put(findSub.apply("TNR B"), new Driver[]{
                findDriver.apply("GMZ_Alfred"), findDriver.apply("LEVITIUS"), findDriver.apply("Lightning_Lorry"),
                findDriver.apply("LotariRacing"), findDriver.apply("Mo_Flavor"), findDriver.apply("Nutcap_1")});
        s4TeamDrivers.put(findSub.apply("TNR C"), new Driver[]{
                findDriver.apply("panicpotato17"), findDriver.apply("Phantom_Steve111"), findDriver.apply("RayCarter"),
                findDriver.apply("Savvy-Unchained"), findDriver.apply("sir_maggs"), findDriver.apply("TNR_Capt_Slow")});
        s4TeamDrivers.put(findSub.apply("AHR 1"), new Driver[]{
                findDriver.apply("AHR_Hills_93"), findDriver.apply("AHR_j_mac"), findDriver.apply("AHR-PezzzaGT"),
                findDriver.apply("AHR-Tankbro"), findDriver.apply("danfn22016"), findDriver.apply("grey_roc")});
        s4TeamDrivers.put(findSub.apply("AHR 2"), new Driver[]{
                findDriver.apply("Jacko_GT7"), findDriver.apply("JackPlayz_01"), findDriver.apply("Lemonz7836"),
                findDriver.apply("miggldeehiggins"), findDriver.apply("OFFICIAL_001"), findDriver.apply("PnR-Proton")});

        // League season (S4 2026): 5 matchdays, 7 matches per MD, 1 race per match
        var s4Teams = new java.util.ArrayList<>(s4TeamDrivers.keySet());
        seedLeagueSeason(s4, s4Teams, s4TeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

        // Swiss season (S2 2024): 5 matchdays, 5 matches per MD, 2 races per match
        var s2TeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
        s2TeamDrivers.put(findParent.apply("P1R"), s4TeamDrivers.get(findParent.apply("P1R")));
        s2TeamDrivers.put(findParent.apply("CLR"), new Driver[]{
                findDriver.apply("BetelgeuzeFIN"), findDriver.apply("chiccoblasi"), findDriver.apply("CLR_Prodigy_97"),
                findDriver.apply("CLR_RichyI78"), findDriver.apply("CSX_Thomas"), findDriver.apply("DylanCliff_28")});
        s2TeamDrivers.put(findParent.apply("TCR"), s4TeamDrivers.get(findParent.apply("TCR")));
        s2TeamDrivers.put(findParent.apply("ART"), s4TeamDrivers.get(findParent.apply("ART")));
        s2TeamDrivers.put(findParent.apply("AHR"), new Driver[]{
                findDriver.apply("AHR_Hills_93"), findDriver.apply("AHR_j_mac"), findDriver.apply("AHR-PezzzaGT"),
                findDriver.apply("AHR-Tankbro"), findDriver.apply("danfn22016"), findDriver.apply("grey_roc")});
        s2TeamDrivers.put(findParent.apply("MRL"), s4TeamDrivers.get(findParent.apply("MRL")));
        s2TeamDrivers.put(findParent.apply("GXR"), s4TeamDrivers.get(findParent.apply("GXR")));
        s2TeamDrivers.put(findParent.apply("DTR"), s4TeamDrivers.get(findParent.apply("DTR")));
        s2TeamDrivers.put(findParent.apply("VEZ"), s4TeamDrivers.get(findParent.apply("VEZ")));
        s2TeamDrivers.put(findParent.apply("TNR"), new Driver[]{
                findDriver.apply("Chaz__CA"), findDriver.apply("D-man371D-man"), findDriver.apply("Deekuhn"),
                findDriver.apply("Dirty_Donavan"), findDriver.apply("Fjneet90"), findDriver.apply("Ghostriderz16173")});
        var s2Teams = new java.util.ArrayList<>(s2TeamDrivers.keySet());
        seedSwissSeason(s2, s2Teams, s2TeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

        // Round Robin (S1 2023 Group A): 3 matchdays, 3 matches per MD, 2 races per match
        var s1aTeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
        s1aTeamDrivers.put(findParent.apply("P1R"), s4TeamDrivers.get(findParent.apply("P1R")));
        s1aTeamDrivers.put(findParent.apply("TCR"), s4TeamDrivers.get(findParent.apply("TCR")));
        s1aTeamDrivers.put(findParent.apply("ART"), s4TeamDrivers.get(findParent.apply("ART")));
        s1aTeamDrivers.put(findParent.apply("MRL"), s4TeamDrivers.get(findParent.apply("MRL")));
        s1aTeamDrivers.put(findParent.apply("GXR"), s4TeamDrivers.get(findParent.apply("GXR")));
        s1aTeamDrivers.put(findSub.apply("CLR 1"), s4TeamDrivers.get(findSub.apply("CLR 1")));
        var s1aTeams = new java.util.ArrayList<>(s1aTeamDrivers.keySet());
        seedRoundRobinSeason(s1a, s1aTeams, s1aTeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

        // Round Robin (S1 2023 Group B): 3 matchdays, 3 matches per MD, 2 races per match
        var s1bTeamDrivers = new java.util.LinkedHashMap<Team, Driver[]>();
        s1bTeamDrivers.put(findParent.apply("DTR"), s4TeamDrivers.get(findParent.apply("DTR")));
        s1bTeamDrivers.put(findParent.apply("VEZ"), s4TeamDrivers.get(findParent.apply("VEZ")));
        s1bTeamDrivers.put(findSub.apply("CLR 2"), s4TeamDrivers.get(findSub.apply("CLR 2")));
        s1bTeamDrivers.put(findSub.apply("TNR A"), s4TeamDrivers.get(findSub.apply("TNR A")));
        s1bTeamDrivers.put(findSub.apply("TNR B"), s4TeamDrivers.get(findSub.apply("TNR B")));
        s1bTeamDrivers.put(findSub.apply("AHR 1"), s4TeamDrivers.get(findSub.apply("AHR 1")));
        var s1bTeams = new java.util.ArrayList<>(s1bTeamDrivers.keySet());
        seedRoundRobinSeason(s1b, s1bTeams, s1bTeamDrivers, homePositions, awayPositions, fastestLapPositions, raceScoring);

        log.info("Seeded matchdays and results: {} matchdays, {} races, {} results",
                matchdayRepository.count(), raceRepository.count(), raceResultRepository.count());
    }

    private void seedLeagueSeason(Season season, java.util.List<Team> teams,
            java.util.Map<Team, Driver[]> teamDrivers,
            int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
            RaceScoring raceScoring) {
        // 5 matchdays, 7 matches per MD (14 teams), 1 race per match
        for (int mdIndex = 0; mdIndex < 5; mdIndex++) {
            var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
            // Pair teams: rotate away teams each matchday
            for (int matchIdx = 0; matchIdx < 7; matchIdx++) {
                int homeIdx = matchIdx;
                int awayIdx = 7 + ((matchIdx + mdIndex) % 7);
                var homeTeam = teams.get(homeIdx);
                var awayTeam = teams.get(awayIdx);
                var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
                seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
                        homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
                        fastestLapPositions[mdIndex]);
            }
        }
    }

    private void seedSwissSeason(Season season, java.util.List<Team> teams,
            java.util.Map<Team, Driver[]> teamDrivers,
            int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
            RaceScoring raceScoring) {
        // 5 matchdays, 5 matches per MD (10 teams), 2 races per match
        for (int mdIndex = 0; mdIndex < 5; mdIndex++) {
            var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
            for (int matchIdx = 0; matchIdx < 5; matchIdx++) {
                int homeIdx = matchIdx;
                int awayIdx = 5 + ((matchIdx + mdIndex) % 5);
                var homeTeam = teams.get(homeIdx);
                var awayTeam = teams.get(awayIdx);
                var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
                // Race 1
                seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
                        homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
                        fastestLapPositions[mdIndex]);
                // Race 2 (reversed positions for variety)
                int nextMd = (mdIndex + 1) % 5;
                seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
                        homePositions[nextMd], homePositions[nextMd], awayPositions[nextMd], awayPositions[nextMd],
                        fastestLapPositions[nextMd]);
            }
        }
    }

    private void seedRoundRobinSeason(Season season, java.util.List<Team> teams,
            java.util.Map<Team, Driver[]> teamDrivers,
            int[][] homePositions, int[][] awayPositions, int[] fastestLapPositions,
            RaceScoring raceScoring) {
        // 3 matchdays, 3 matches per MD (6 teams), 2 races per match
        for (int mdIndex = 0; mdIndex < 3; mdIndex++) {
            var md = matchdayRepository.save(new Matchday(season, "Matchday " + (mdIndex + 1), mdIndex + 1));
            // Round-robin pairing: pair team[i] with team[5-i], rotating each matchday
            for (int matchIdx = 0; matchIdx < 3; matchIdx++) {
                int homeIdx = matchIdx;
                int awayIdx = 3 + ((matchIdx + mdIndex) % 3);
                var homeTeam = teams.get(homeIdx);
                var awayTeam = teams.get(awayIdx);
                var match = matchRepository.save(new Match(md, homeTeam, awayTeam));
                // Race 1
                seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
                        homePositions[mdIndex], homePositions[mdIndex], awayPositions[mdIndex], awayPositions[mdIndex],
                        fastestLapPositions[mdIndex]);
                // Race 2 (reversed positions)
                int nextMd = (mdIndex + 1) % 5;
                seedRace(md, match, raceScoring, teamDrivers.get(homeTeam), teamDrivers.get(awayTeam),
                        homePositions[nextMd], homePositions[nextMd], awayPositions[nextMd], awayPositions[nextMd],
                        fastestLapPositions[nextMd]);
            }
        }
    }

    private void seedRace(Matchday md, Match match, RaceScoring raceScoring,
            Driver[] homeDrivers, Driver[] awayDrivers,
            int[] homeRacePositions, int[] homeQualiPositions,
            int[] awayRacePositions, int[] awayQualiPositions,
            int fastestLapPosition) {
        var race = new Race();
        race.setMatchday(md);
        race.setMatch(match);
        raceRepository.save(race);
        race.setSettings(createTestSettings(race));
        raceRepository.save(race);

        // Save lineups first (Source of Truth per CLAUDE.md)
        for (var d : homeDrivers) raceLineupRepository.save(new RaceLineup(race, d, match.getHomeTeam()));
        for (var d : awayDrivers) raceLineupRepository.save(new RaceLineup(race, d, match.getAwayTeam()));

        // Save results and calculate points
        var results = new java.util.ArrayList<RaceResult>();
        for (int i = 0; i < homeDrivers.length; i++) {
            boolean fl = (homeRacePositions[i] == fastestLapPosition);
            results.add(new RaceResult(race, homeDrivers[i], homeRacePositions[i], homeQualiPositions[i], fl));
        }
        for (int i = 0; i < awayDrivers.length; i++) {
            boolean fl = (awayRacePositions[i] == fastestLapPosition);
            results.add(new RaceResult(race, awayDrivers[i], awayRacePositions[i], awayQualiPositions[i], fl));
        }
        raceResultRepository.saveAll(results);
        scoringService.calculatePoints(results, raceScoring);
        raceResultRepository.saveAll(results);
        raceResultRepository.flush();

        // Detach and reload race to ensure results collection is populated for aggregation (Pitfall 2)
        entityManager.detach(race);
        var reloadedRace = raceRepository.findById(race.getId()).orElseThrow();
        scoringService.aggregateMatchScores(reloadedRace);
        matchRepository.save(reloadedRace.getMatch());
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
