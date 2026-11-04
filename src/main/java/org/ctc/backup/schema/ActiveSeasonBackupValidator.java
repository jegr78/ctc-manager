package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;

public final class ActiveSeasonBackupValidator {
    private boolean activeSeen;

    public void accept(String table, JsonNode row) {
        if (!"seasons".equals(table) || !row.path("active").asBoolean(false)) return;
        if (activeSeen) throw new ValidationException("The backup contains more than one active season. Review its active-season flags before restoring.");
        activeSeen = true;
    }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
