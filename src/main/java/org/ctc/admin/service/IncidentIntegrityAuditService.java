package org.ctc.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.IncidentBackupValidator;
import org.ctc.backup.service.BackupExportService;
import org.ctc.domain.model.IncidentDelivery;
import org.ctc.domain.model.IncidentDeliveryState;
import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.IncidentReportWindow;
import org.ctc.domain.model.IncidentRulingPublication;
import org.ctc.domain.model.IncidentThreadPublication;
import org.ctc.domain.model.IncidentThreadStage;
import org.ctc.domain.model.IncidentWindowState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentIntegrityAuditService {
    private final BackupSchema schema;
    private final BackupExportService exports;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IncidentIntegrityAuditService(BackupSchema schema, BackupExportService exports,
                                         @Qualifier("backupObjectMapper") ObjectMapper mapper, Clock clock) {
        this.schema = schema; this.exports = exports; this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DataAuditFinding> inspect() {
        var validator = new IncidentBackupValidator(BackupSchema.SCHEMA_VERSION);
        var operationalRows = new ArrayList<Object>();
        try {
            for (var entity : schema.getExportOrder()) {
                if (!IncidentBackupValidator.supports(entity.tableName())) continue;
                for (var row : exports.fetchAllForBackup(entity.entityClass())) {
                    validator.accept(entity.tableName(), mapper.valueToTree(row));
                    if (row instanceof IncidentMatchContext || row instanceof IncidentReportWindow
                            || row instanceof IncidentDelivery || row instanceof IncidentThreadPublication
                            || row instanceof IncidentRulingPublication) operationalRows.add(row);
                }
            }
            validator.validate();
            var findings = new ArrayList<DataAuditFinding>();
            var observedAt = clock.instant();
            for (var row : operationalRows) inspectOperational(row, observedAt, findings);
            return List.copyOf(findings);
        } catch (IncidentBackupValidator.ValidationException failure) {
            return List.of(new DataAuditFinding(DataAuditFinding.Category.INCIDENT_REPORTS,
                    DataAuditFinding.Resolution.AMBIGUOUS, validator.subject(), failure.getMessage(),
                    "Review the original pairing, race and immutable decisions against authoritative records; preserve revisions and receipts."));
        }
    }

    private static void inspectOperational(Object row, Instant observedAt, List<DataAuditFinding> findings) {
        if (row instanceof IncidentReportWindow window && window.getState() == IncidentWindowState.OPEN
                && !observedAt.isBefore(window.getDeadline())) {
            findings.add(review("Reporting window " + window.getId(),
                    "Reporting window remains OPEN at or after its confirmed deadline.",
                    "Check the incident delivery worker and use the existing cutoff workflow; preserve the confirmed deadline and report history."));
        }
        if (row instanceof IncidentMatchContext context) {
            var home = context.getHomeTeam(); var away = context.getAwayTeam();
            String role = home.getEffectiveDiscordRoleId();
            if (role != null && role.matches("[0-9]{1,32}") && role.equals(away.getEffectiveDiscordRoleId())
                    && !home.getParentOrSelf().getId().equals(away.getParentOrSelf().getId())) {
                findings.add(review("Incident context " + context.getId(),
                        "Both participants share a Discord role without one common organization.",
                        "Review the current team/organization role configuration; retain the reporting parties already accepted in permanent cases."));
            }
        }
        if (row instanceof IncidentDelivery delivery && unresolved(delivery.getState())) {
            findings.add(review("Incident delivery " + delivery.getId(),
                    delivery.getOperation().name() + " delivery is " + delivery.getState().name() + ".",
                    "Inspect public delivery status and the known destination; use verified recovery controls for uncertain outcomes."));
        }
        if (row instanceof IncidentRulingPublication publication && unresolved(publication.getState())) {
            findings.add(review("Ruling publication " + publication.getId(),
                    "Ruling delivery is " + publication.getState().name() + ".",
                    "Review the exact approved ruling in its known match thread; preserve the prior confirmed ruling until the outcome is verified."));
        }
        if (row instanceof IncidentThreadPublication publication && publication.getPrivateStarterId() != null
                && publication.stage() != IncidentThreadStage.COMPLETE) {
            findings.add(review("Incident thread publication " + publication.getId(),
                    "Linked incident discussion delivery is PARTIAL.",
                    "Review the stored thread pair and complete only a verified missing step through the existing delivery recovery controls."));
        }
    }

    private static boolean unresolved(IncidentDeliveryState state) {
        return state == IncidentDeliveryState.FAILED || state == IncidentDeliveryState.UNCERTAIN;
    }

    private static DataAuditFinding review(String subject, String evidence, String correction) {
        return new DataAuditFinding(DataAuditFinding.Category.INCIDENT_REPORTS,
                DataAuditFinding.Resolution.AMBIGUOUS, subject, evidence, correction);
    }
}
