package org.ctc.backup.schema;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 72 / Plan 01 — Wave 0 stub. Boots Spring and asserts BackupSchema is correctly wired.
 *
 * <p>RED until Tasks 2-4 land BackupSchema + EntityRef + EntityTopoSorter.
 *
 * <p>Covers the four invariants the production code must satisfy:
 * <ul>
 *   <li>size == 24 (D-03 amended: 23 + PlayoffRound)</li>
 *   <li>FK-respecting ordering (every owning-side {@code @ManyToOne}/{@code @OneToOne}
 *       dependency precedes its owner)</li>
 *   <li>immutability ({@code List.copyOf})</li>
 *   <li>Team self-FK (parentTeam) is detected and skipped — Team appears exactly once</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSchemaTopologyIT {

    @Autowired
    private BackupSchema backupSchema;

    @Test
    void givenSpringContext_whenGetExportOrder_thenReturns26Entities() {
        // when
        List<EntityRef> exportOrder = backupSchema.getExportOrder();
        // then — 24 league entities under org.ctc.domain.model + 2 Discord entities under
        // org.ctc.discord.model (DiscordGlobalConfig + DiscordPost) = 26 total.
        assertThat(exportOrder).hasSize(26);
    }

    @Test
    void givenSpringContext_whenGetExportOrder_thenManyToOneDependenciesPrecedeOwners() {
        // when
        List<EntityRef> exportOrder = backupSchema.getExportOrder();
        Map<Class<?>, Integer> indexByClass = new HashMap<>();
        for (int i = 0; i < exportOrder.size(); i++) {
            indexByClass.put(exportOrder.get(i).entityClass(), i);
        }

        // then — every @ManyToOne / owning @OneToOne dependency precedes its owner
        for (EntityRef owner : exportOrder) {
            for (Field f : owner.entityClass().getDeclaredFields()) {
                ManyToOne m2o = f.getAnnotation(ManyToOne.class);
                OneToOne o2o = f.getAnnotation(OneToOne.class);
                if (m2o == null && (o2o == null || !o2o.mappedBy().isEmpty())) {
                    continue;
                }
                Class<?> depClass = f.getType();
                if (!indexByClass.containsKey(depClass)) {
                    continue;                                        // FK to non-domain-model class
                }
                if (depClass.equals(owner.entityClass())) {
                    continue;                                        // Team.parentTeam self-FK
                }
                assertThat(indexByClass.get(depClass))
                        .as("dependency %s must precede owner %s (field %s)",
                                depClass.getSimpleName(), owner.entityClass().getSimpleName(), f.getName())
                        .isLessThan(indexByClass.get(owner.entityClass()));
            }
        }
    }

    @Test
    void givenSpringContext_whenGetExportOrder_thenReturnedListIsImmutable() {
        // when / then
        assertThatThrownBy(() -> backupSchema.getExportOrder().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void givenTeamSelfFK_whenGetExportOrder_thenTeamAppearsExactlyOnce() {
        // when
        long teamCount = backupSchema.getExportOrder().stream()
                .filter(r -> r.entityClass().equals(Team.class))
                .count();
        // then — self-FK Team.parentTeam must NOT cause duplicate or deadlock
        assertThat(teamCount).isEqualTo(1L);
    }
}
