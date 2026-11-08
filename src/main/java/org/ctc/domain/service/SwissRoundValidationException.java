package org.ctc.domain.service;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;

public class SwissRoundValidationException extends BusinessRuleException {
    public record FieldIssue(String field, String message) { }
    private final List<FieldIssue> issues;
    public SwissRoundValidationException(List<FieldIssue> issues) {
        super("Review the round's highlighted fields before saving."); this.issues=List.copyOf(issues);
    }
    public List<FieldIssue> issues() { return issues; }
}
