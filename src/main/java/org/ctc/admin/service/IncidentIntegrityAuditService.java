package org.ctc.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.IncidentBackupValidator;
import org.ctc.backup.service.BackupExportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentIntegrityAuditService {
    private final BackupSchema schema;
    private final BackupExportService exports;
    private final ObjectMapper mapper;

    public IncidentIntegrityAuditService(BackupSchema schema, BackupExportService exports,
                                         @Qualifier("backupObjectMapper") ObjectMapper mapper) {
        this.schema = schema; this.exports = exports; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<DataAuditFinding> inspect() {
        var validator = new IncidentBackupValidator(BackupSchema.SCHEMA_VERSION);
        try {
            for (var entity : schema.getExportOrder()) {
                if (!IncidentBackupValidator.supports(entity.tableName())) continue;
                for (var row : exports.fetchAllForBackup(entity.entityClass())) {
                    validator.accept(entity.tableName(), mapper.valueToTree(row));
                }
            }
            validator.validate();
            return List.of();
        } catch (IncidentBackupValidator.ValidationException failure) {
            return List.of(new DataAuditFinding(DataAuditFinding.Category.INCIDENT_REPORTS,
                    DataAuditFinding.Resolution.AMBIGUOUS, validator.subject(), failure.getMessage(),
                    "Review the original pairing, race and immutable decisions against authoritative records; preserve revisions and receipts."));
        }
    }
}
