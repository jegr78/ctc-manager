package org.ctc.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.PickBackupValidator;
import org.ctc.backup.service.BackupExportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PickIntegrityAuditService {
    private final BackupSchema schema;
    private final BackupExportService exports;
    private final ObjectMapper mapper;

    public PickIntegrityAuditService(BackupSchema schema, BackupExportService exports,
                                    @Qualifier("backupObjectMapper") ObjectMapper mapper) {
        this.schema = schema;
        this.exports = exports;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<DataAuditFinding> inspect() {
        var validator = new PickBackupValidator(BackupSchema.SCHEMA_VERSION);
        try {
            for (var entity : schema.getExportOrder()) {
                if (!PickBackupValidator.supports(entity.tableName())) continue;
                for (var row : exports.fetchAllForBackup(entity.entityClass())) {
                    validator.accept(entity.tableName(), mapper.valueToTree(row));
                }
            }
            validator.validate();
            return List.of();
        } catch (PickBackupValidator.ValidationException failure) {
            return List.of(new DataAuditFinding(DataAuditFinding.Category.PRIVATE_PICKS,
                    DataAuditFinding.Resolution.AMBIGUOUS, "Confidential pick history", failure.getMessage(),
                    "Review the original target and immutable history from authoritative evidence; preserve revisions and receipts."));
        }
    }
}
