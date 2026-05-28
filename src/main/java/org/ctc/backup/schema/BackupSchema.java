package org.ctc.backup.schema;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.model.DiscordPost;
import org.springframework.stereotype.Component;

/**
 * Backup wire contract.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@link #SCHEMA_VERSION} — integer constant; bumped on every wire-incompatible
 *       schema change.</li>
 *   <li>{@link #getExportOrder()} — FK-respecting topological order over all
 *       {@code org.ctc.domain.model.*} and {@code org.ctc.discord.model.*} entities,
 *       generated at startup from the JPA Metamodel. Used by {@code BackupExportService}.</li>
 * </ul>
 *
 * <p>Structural exclusion: any entity placed under {@code org.ctc.backup.*} (notably
 * {@code DataImportAudit}) is filtered out by the package-name gate below. This is the
 * canonical enforcement mechanism for keeping audit data out of backups — no marker
 * annotation, no opt-in list, no developer memory required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupSchema {

    public static final int SCHEMA_VERSION = 2;

    private static final Set<Class<?>> FK_TAIL_ENTITIES = Set.of(DiscordPost.class);

    private final EntityManagerFactory entityManagerFactory;
    private final EntityTopoSorter entityTopoSorter;

    private List<EntityRef> exportOrder;

    @PostConstruct
    void initializeExportOrder() {
        var entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(et -> {
                    String pkg = et.getJavaType().getPackage().getName();
                    return pkg.startsWith("org.ctc.domain.model")
                            || pkg.startsWith("org.ctc.discord.model");
                })
                .toList();
        List<EntityRef> sorted = entityTopoSorter.sort(entityTypes);
        this.exportOrder = List.copyOf(pinFkEntitiesLast(sorted, FK_TAIL_ENTITIES));
        log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]",
                SCHEMA_VERSION,
                exportOrder.size(),
                exportOrder.stream().map(EntityRef::tableName).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    /**
     * Moves entities with {@code @Column UUID} FK fields to the tail of the topo-sorted order.
     *
     * <p>{@link EntityTopoSorter} only follows {@code @ManyToOne} / {@code @OneToOne} edges, so
     * an entity that exposes its FKs as bare {@code @Column UUID} columns (e.g.,
     * {@link DiscordPost}'s {@code match_id} / {@code matchday_id} / {@code race_id} /
     * {@code season_id} / {@code phase_id}) is treated as in-degree-0 and may land before its
     * FK parents. Pinning by entity-class identity guarantees the parent rows exist at restore
     * time and survives a future table-name rename. Wipe order is the reverse of the export
     * order, so these entities are also wiped first — correct for child-before-parent deletion.
     */
    private static List<EntityRef> pinFkEntitiesLast(List<EntityRef> sorted, Set<Class<?>> fkEntities) {
        List<EntityRef> reordered = new ArrayList<>(sorted.size());
        List<EntityRef> tail = new ArrayList<>();
        for (EntityRef ref : sorted) {
            if (fkEntities.contains(ref.entityClass())) {
                tail.add(ref);
            } else {
                reordered.add(ref);
            }
        }
        reordered.addAll(tail);
        return reordered;
    }

    public List<EntityRef> getExportOrder() {
        return exportOrder;
    }
}
