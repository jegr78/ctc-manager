package org.ctc.backup.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational audit log of backup imports.
 *
 * <p>Phase 72 ships this entity inert (no writes, no service surface). Phase 75 wires
 * {@code DataImportAuditRepository.save(...)} into the {@code BackupImportService}
 * transaction with one row per import attempt (success or failure).
 *
 * <p><strong>Intentionally does NOT extend {@code BaseEntity}.</strong> The {@code executedAt}
 * field is set explicitly by the Phase 75 writer; the {@code AuditingEntityListener} bypass is
 * exactly what Phase 75's IMPORT-05 transaction is designed to enable. Inheriting
 * {@code BaseEntity} would re-introduce {@code @CreatedDate}/{@code @LastModifiedDate}
 * overrides on import-row writes, defeating the design.
 *
 * <p><strong>NOT a Java record.</strong> Hibernate 7 / Jakarta Persistence does not support
 * record-based entities (records are final, immutable, and not proxyable). See
 * Phase 72 RESEARCH §Pitfall P-1.
 *
 * <p><strong>Lives in {@code org.ctc.backup.audit}, NOT {@code org.ctc.domain.model}.</strong>
 * This package placement is the canonical IMPORT-08 enforcement mechanism: Phase 72's
 * {@code BackupSchema.@PostConstruct} filters JPA Metamodel entities by
 * {@code startsWith("org.ctc.domain.model")}, so this class is structurally excluded from
 * {@code BackupSchema.exportOrder} — no marker annotation, no opt-in, no developer memory.
 */
@Entity
@Table(name = "data_import_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DataImportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @NotBlank
    @Column(name = "executed_by", nullable = false)
    private String executedBy;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @NotBlank
    @Column(name = "table_counts_wiped", nullable = false, columnDefinition = "LONGTEXT")
    private String tableCountsWiped;        // JSON shape enforced at write time by Jackson; DDL is LONGTEXT on MariaDB (H2 maps to VARCHAR)

    @NotBlank
    @Column(name = "table_counts_restored", nullable = false, columnDefinition = "LONGTEXT")
    private String tableCountsRestored;     // JSON shape enforced at write time by Jackson; DDL is LONGTEXT on MariaDB (H2 maps to VARCHAR)

    @NotBlank
    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(nullable = false)
    private boolean success;
}
