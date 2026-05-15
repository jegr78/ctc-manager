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
 * Operational audit log of backup imports — one row per import attempt (success or failure).
 *
 * <p><strong>Does NOT extend {@code BaseEntity}.</strong> {@code executedAt} is set explicitly;
 * bypassing {@code AuditingEntityListener} ensures imported timestamps survive rather than
 * being overwritten by {@code @CreatedDate}/{@code @LastModifiedDate}.
 *
 * <p><strong>NOT a Java record.</strong> Hibernate / Jakarta Persistence does not support
 * record-based entities (records are final, immutable, and not proxyable).
 *
 * <p><strong>Lives in {@code org.ctc.backup.audit}, NOT {@code org.ctc.domain.model}.</strong>
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
