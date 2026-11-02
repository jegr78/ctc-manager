package org.ctc.domain.service;

import java.sql.SQLException;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.dao.ConcurrencyFailureException;

public final class RaceSelectionConcurrency {
    private RaceSelectionConcurrency() {}

    public static RuntimeException translate(RuntimeException failure) {
        if (failure instanceof BusinessRuleException) return failure;
        boolean conflict = failure instanceof ConcurrencyFailureException;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1020 && "HY000".equals(sql.getSQLState())) {
                conflict = true;
            }
        }
        if (!conflict) return failure;
        var result = new BusinessRuleException("Selections changed while saving. Reload the race and try again.");
        result.initCause(failure);
        return result;
    }
}
