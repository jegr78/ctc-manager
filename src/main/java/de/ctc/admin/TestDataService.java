package de.ctc.admin;

import de.ctc.domain.model.Driver;
import de.ctc.domain.model.Season;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.DriverRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
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

    @Transactional
    public void seed() {
        if (seasonRepository.count() > 0) {
            log.debug("Seed data already present, skipping");
            return;
        }
        var teams = seedTeams();
        seedSubTeams(teams);
        seedSeasons(teams);
        seedDrivers();
        log.info("Seed data created: {} teams, {} seasons, {} drivers",
                teamRepository.count(), seasonRepository.count(), driverRepository.count());
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

    private void seedSeasons(List<Team> parentTeams) {
        var allTeams = teamRepository.findAll();

        // Helper to find teams by shortName (optionally sub-team by name)
        java.util.function.BiFunction<String, String, Team> findTeam = (shortName, name) ->
                allTeams.stream()
                        .filter(t -> t.getShortName().equals(shortName) && (name == null || t.getName().equals(name)))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Team not found: " + shortName + "/" + name));

        // Older seasons: all parent teams
        for (String name : List.of("Season 1 - 2023 - Group A", "Season 1 - 2023 - Group B", "Season 2 - 2024")) {
            var season = new Season(name);
            season.getTeams().addAll(parentTeams);
            seasonRepository.save(season);
        }

        // Season 3 - 2025 - Group A: P1Rx, CLR, MRL, TCR, GXR
        var s3a = new Season("Season 3 - 2025 - Group A");
        s3a.getTeams().addAll(List.of(
                findTeam.apply("P1Rx", "Project One Racing X"),
                findTeam.apply("CLR", "Community League Racing"),
                findTeam.apply("MRL", "Medway Racing League"),
                findTeam.apply("TCR", "Tidgney Community Racing"),
                findTeam.apply("GXR", "Gen-X Racing")
        ));
        seasonRepository.save(s3a);

        // Season 3 - 2025 - Group B: P1R (sub-team), AHR, DTR, ART
        var s3b = new Season("Season 3 - 2025 - Group B");
        s3b.getTeams().addAll(List.of(
                findTeam.apply("P1R", "Project One Racing"),  // sub-team of P1R parent
                findTeam.apply("AHR", "Apex Hunter Racing"),
                findTeam.apply("DTR", "Dream Team Racing"),
                findTeam.apply("ART", "Amigos Racing Team")
        ));
        seasonRepository.save(s3b);

        // Season 4 - 2026: CLR 1/2, TNR A/B/C, DTR, MRL, ART, AHR 1/2, VEZ, GXR, TCR
        var s4 = new Season("Season 4 - 2026");
        s4.setActive(true);
        s4.getTeams().addAll(List.of(
                findTeam.apply("CLR 1", "Community League Racing 1"),
                findTeam.apply("CLR 2", "Community League Racing 2"),
                findTeam.apply("TNR A", "The Neutrals Racing A"),
                findTeam.apply("TNR B", "The Neutrals Racing B"),
                findTeam.apply("TNR C", "The Neutrals Racing C"),
                findTeam.apply("DTR", "Dream Team Racing"),
                findTeam.apply("MRL", "Medway Racing League"),
                findTeam.apply("ART", "Amigos Racing Team"),
                findTeam.apply("AHR 1", "Apex Hunter Racing 1"),
                findTeam.apply("AHR 2", "Apex Hunter Racing 2"),
                findTeam.apply("VEZ", "VEZ Racing Team"),
                findTeam.apply("GXR", "Gen-X Racing"),
                findTeam.apply("TCR", "Tidgney Community Racing")
        ));
        seasonRepository.save(s4);
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

    private void driver(String psnId, String nickname) {
        driverRepository.save(new Driver(psnId, nickname));
    }
}
