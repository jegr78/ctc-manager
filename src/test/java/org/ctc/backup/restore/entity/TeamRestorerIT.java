package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 75 / Plan 07 — Failsafe IT for {@link TeamRestorer} against a real H2 database.
 *
 * <p>The Plan 03 unit test ({@code TeamRestorerTest}) verifies the JDBC contract via a mocked
 * {@link JdbcTemplate}. This IT proves the 2-pass discipline (CONTEXT D-06) works end-to-end
 * against an actual database with the {@code parent_team_id} self-FK constraint enforced:
 * Pass 1 inserts every row with {@code parent_team_id=NULL}, Pass 2 issues
 * {@code UPDATE teams SET parent_team_id = ? WHERE id = ?} for the subset whose source JSON
 * has a non-null {@code parentTeam} UUID.
 *
 * <p><strong>JSON shape (from {@code TeamMixIn}):</strong> {@code parentTeam} renders as a bare
 * UUID string via {@code @JsonIdentityReference(alwaysAsId=true)} — NOT a nested
 * {@code {id:"..."}} object. The test JSON fixtures mirror that exact shape so the IT
 * exercises the production restorer code without translation.
 *
 * <p>Each test runs under {@code @Transactional} + {@code @Rollback(true)} so the dev-profile
 * {@code DevDataSeeder} fixture is undisturbed across test runs. Pre-test cleanup
 * ({@code UPDATE teams SET parent_team_id=NULL} + {@code DELETE FROM teams}) is rolled back
 * together with the restorer's inserts when the test method returns.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@Tag("integration")
class TeamRestorerIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TeamRestorer restorer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    BackupSchema backupSchema;

    @BeforeEach
    void wipeAllTablesFkReverse() {
        // The dev-profile fixture seeds rows in many tables that ultimately FK back into
        // teams (race_lineups, matches, races, season_drivers, season_teams, playoff_seeds,
        // playoff_matchups, phase_teams, ...). Wiping only the side-tables that reference
        // teams would still leave indirect references via races → race_lineups. The simplest
        // safe option is to wipe everything in FK-reverse order, mirroring exactly what
        // BackupImportService.wipeAllTables() does in production (Plan 06).
        //
        // We do this inside the @Transactional test method so the dev fixture is restored
        // when the test rolls back at method end.
        jdbcTemplate.execute("UPDATE teams SET parent_team_id = NULL");
        jdbcTemplate.execute("UPDATE season_teams SET successor_season_team_id = NULL");
        jdbcTemplate.execute("UPDATE playoff_matchups SET next_matchup_id = NULL");
        for (EntityRef ref : backupSchema.getExportOrder().reversed()) {
            jdbcTemplate.execute("DELETE FROM " + ref.tableName());
        }
    }

    @Test
    void givenTeamsJsonWithParentChildRelations_whenRestoreInvoked_thenAllTeamsAndParentTeamFKsRestoredOnH2()
            throws Exception {
        // given — 3 teams in flat JSON-array order:
        //   T1 (root, parentTeam=null)
        //   T2 (child of T1)
        //   T3 (child of T1)
        // The order is deliberately "flat" — T2 references T1 before T1's pass-2 update is
        // even issued. Pass-1 NULL-insert + pass-2 FK-update is the only correct path.
        UUID t1Id = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");
        UUID t2Id = UUID.fromString("22222222-aaaa-aaaa-aaaa-222222222222");
        UUID t3Id = UUID.fromString("33333333-aaaa-aaaa-aaaa-333333333333");

        JsonNode t1 = MAPPER.readTree("""
                {
                  "id": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "name": "Parent Racing",
                  "shortName": "T-PAR",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode t2 = MAPPER.readTree("""
                {
                  "id": "22222222-aaaa-aaaa-aaaa-222222222222",
                  "name": "Parent Racing Group A",
                  "shortName": "T-PARA",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "createdAt": "2024-01-02T00:00:00",
                  "updatedAt": "2024-01-02T00:00:00"
                }""");
        JsonNode t3 = MAPPER.readTree("""
                {
                  "id": "33333333-aaaa-aaaa-aaaa-333333333333",
                  "name": "Parent Racing Group B",
                  "shortName": "T-PARB",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "createdAt": "2024-01-03T00:00:00",
                  "updatedAt": "2024-01-03T00:00:00"
                }""");

        // when
        restorer.restore(List.of(t1, t2, t3), jdbcTemplate);

        // Force the Hibernate L1 cache to re-load — the restorer used raw JDBC.
        // teamRepository.findById() may otherwise return stale managed entities.
        teamRepository.flush();

        // then — all 3 rows persisted, sub-teams reference T1 via parent_team_id
        assertThat(teamRepository.count())
                .as("All 3 rows must be persisted")
                .isEqualTo(3);

        Team parent = teamRepository.findById(t1Id).orElseThrow();
        assertThat(parent.getParentTeam())
                .as("Root team must have parentTeam = null")
                .isNull();
        assertThat(parent.getShortName()).isEqualTo("T-PAR");

        Team childA = teamRepository.findById(t2Id).orElseThrow();
        assertThat(childA.getParentTeam())
                .as("Pass-2 must have wired the sub-team FK on T2")
                .isNotNull();
        assertThat(childA.getParentTeam().getId())
                .as("T2.parent_team_id must equal T1.id")
                .isEqualTo(t1Id);

        Team childB = teamRepository.findById(t3Id).orElseThrow();
        assertThat(childB.getParentTeam())
                .as("Pass-2 must have wired the sub-team FK on T3")
                .isNotNull();
        assertThat(childB.getParentTeam().getId())
                .as("T3.parent_team_id must equal T1.id")
                .isEqualTo(t1Id);

        // Direct JDBC sanity: row-count of sub-team rows matches the count we built into the JSON
        Long subTeamRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teams WHERE parent_team_id IS NOT NULL", Long.class);
        assertThat(subTeamRowCount)
                .as("teams.parent_team_id IS NOT NULL row count must equal 2")
                .isEqualTo(2L);
    }

    @Test
    void givenTeamsWithoutAnySubTeams_whenRestoreInvoked_thenPass2Skipped() throws Exception {
        // given — 2 root-level teams, NO parentTeam references at all
        JsonNode rootA = MAPPER.readTree("""
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "name": "Alpha",
                  "shortName": "T-ALPHA",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode rootB = MAPPER.readTree("""
                {
                  "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "name": "Beta",
                  "shortName": "T-BETA",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");

        // when
        restorer.restore(List.of(rootA, rootB), jdbcTemplate);

        // then — both rows persisted, NO row has a non-null parent_team_id
        Long subTeamRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teams WHERE parent_team_id IS NOT NULL", Long.class);
        assertThat(subTeamRowCount)
                .as("teams.parent_team_id IS NOT NULL must be 0 when no sub-teams exist")
                .isEqualTo(0L);

        Long totalRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teams", Long.class);
        assertThat(totalRowCount)
                .as("Total teams row count must equal the 2 inserted rows")
                .isEqualTo(2L);

        // Pass-2 skip is observable via the FK count (zero) + the total count being exactly the
        // input size. The TeamRestorer unit test (TeamRestorerTest) verifies the additional
        // detail that the second batchUpdate is never invoked on the JdbcTemplate — that
        // assertion needs a mocked JdbcTemplate which a real-DB IT cannot provide.
    }
}
