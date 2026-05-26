package org.ctc.backup.schema;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        this.exportOrder = List.copyOf(pinDiscordPostLast(sorted));
        log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]",
                SCHEMA_VERSION,
                exportOrder.size(),
                exportOrder.stream().map(EntityRef::tableName).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    /**
     * Moves {@code discord_post} to the end of the topo-sorted order.
     *
     * <p>{@code DiscordPost} exposes its five FK columns ({@code match_id}, {@code matchday_id},
     * {@code race_id}, {@code season_id}, {@code phase_id}) as {@code @Column UUID} rather than
     * {@code @ManyToOne} JPA associations, so {@link EntityTopoSorter} cannot detect the
     * dependency edges and treats {@code DiscordPost} as in-degree-0 — potentially placing it
     * before its FK parents. Pinning it last guarantees every parent row exists at restore
     * time and the FK constraints from V12 / V14 hold. Wipe order is the reverse of the export
     * order, so {@code discord_post} is also wiped first — correct for child-before-parent
     * deletion.
     */
    private static List<EntityRef> pinDiscordPostLast(List<EntityRef> sorted) {
        List<EntityRef> reordered = new ArrayList<>(sorted.size());
        EntityRef discordPost = null;
        for (EntityRef ref : sorted) {
            if ("discord_post".equals(ref.tableName())) {
                discordPost = ref;
            } else {
                reordered.add(ref);
            }
        }
        if (discordPost != null) {
            reordered.add(discordPost);
        }
        return reordered;
    }

    public List<EntityRef> getExportOrder() {
        return exportOrder;
    }
}
