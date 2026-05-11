package org.ctc.backup.schema;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 72 — Backup Wire Contract.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@link #SCHEMA_VERSION} — integer constant; bumped on every wire-incompatible
 *       schema change (GAP-2 resolution).</li>
 *   <li>{@link #getExportOrder()} — FK-respecting topological order over all
 *       {@code org.ctc.domain.model.*} entities, generated at startup from the JPA
 *       Metamodel (D-04). Used by Phase 73's {@code BackupExportService}.</li>
 * </ul>
 *
 * <p>D-06 structural exclusion: any entity placed under {@code org.ctc.backup.*} (notably
 * {@code DataImportAudit}) is filtered out by the package-name gate below. This is the
 * canonical IMPORT-08 enforcement mechanism — no marker annotation, no opt-in list,
 * no developer memory required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupSchema {

    public static final int SCHEMA_VERSION = 1;

    private final EntityManagerFactory entityManagerFactory;
    private final EntityTopoSorter entityTopoSorter;

    private List<EntityRef> exportOrder;

    @PostConstruct
    void initializeExportOrder() {
        var entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(et -> et.getJavaType().getPackage().getName().startsWith("org.ctc.domain.model"))
                .toList();
        this.exportOrder = List.copyOf(entityTopoSorter.sort(entityTypes));
        log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]",
                SCHEMA_VERSION,
                exportOrder.size(),
                exportOrder.stream().map(EntityRef::tableName).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    public List<EntityRef> getExportOrder() {
        return exportOrder;
    }
}
