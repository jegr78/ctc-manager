package org.ctc.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.PickBackupValidator;
import org.ctc.backup.service.BackupExportService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.MatchdayPick;
import org.ctc.domain.model.PickState;
import org.ctc.domain.model.PickTargetRules;
import org.ctc.domain.model.Race;
import org.ctc.domain.service.PickDeadlinePolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PickIntegrityAuditService {
    private final BackupSchema schema;
    private final BackupExportService exports;
    private final ObjectMapper mapper;
    private final PickDeadlinePolicy deadlines;

    public PickIntegrityAuditService(BackupSchema schema, BackupExportService exports,
                                    @Qualifier("backupObjectMapper") ObjectMapper mapper, PickDeadlinePolicy deadlines) {
        this.schema = schema;
        this.exports = exports;
        this.mapper = mapper;
        this.deadlines = deadlines;
    }

    @Transactional(readOnly = true)
    public List<DataAuditFinding> inspect() {
        var validator = new PickBackupValidator(BackupSchema.SCHEMA_VERSION);
        var managedRaces = new ArrayList<Race>();
        var picks = new HashMap<java.util.UUID, MatchdayPick>();
        try {
            for (var entity : schema.getExportOrder()) {
                if (!PickBackupValidator.supports(entity.tableName())) continue;
                for (var row : exports.fetchAllForBackup(entity.entityClass())) {
                    if (row instanceof Race race && race.isPicksManaged()) managedRaces.add(race);
                    if (row instanceof MatchdayPick pick) picks.put(pick.getRace().getId(), pick);
                    validator.accept(entity.tableName(), mapper.valueToTree(row));
                }
            }
            validator.validate();
            for (var race : managedRaces) {
                var pick = picks.get(race.getId());
                if (!PickTargetRules.isUnfinishedTarget(race)
                        || pick != null && (pick.isManualOpen() || pick.isManualClosed() || pick.getState() == PickState.APPROVED)) continue;
                var deadline = race.getMatchday().getPickDeadline();
                if (deadline == null) continue;
                try {
                    deadlines.resolveDeadline(deadline);
                } catch (BusinessRuleException unresolved) {
                    return List.of(new DataAuditFinding(DataAuditFinding.Category.PRIVATE_PICKS,
                            DataAuditFinding.Resolution.AMBIGUOUS, "Race " + race.getId(),
                            "Deadline " + deadline + " Europe/Berlin requires management review",
                            "Review the matchday deadline or record an explicit management opening or closure; do not infer a cutoff."));
                }
            }
            return List.of();
        } catch (PickBackupValidator.ValidationException failure) {
            return List.of(new DataAuditFinding(DataAuditFinding.Category.PRIVATE_PICKS,
                    DataAuditFinding.Resolution.AMBIGUOUS, "Private picks and channel bindings", failure.getMessage(),
                    "Review channel bindings, the original target and immutable history against authoritative records; preserve revisions and receipts."));
        }
    }
}
