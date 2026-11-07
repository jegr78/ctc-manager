package org.ctc.admin.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.ctc.domain.model.SeasonDocumentLinks;

public class SeasonDocumentUrlValidator implements ConstraintValidator<SeasonDocumentUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            SeasonDocumentLinks.normalize(value);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
