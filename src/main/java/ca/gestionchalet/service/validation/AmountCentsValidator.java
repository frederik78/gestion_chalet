package ca.gestionchalet.service.validation;

import ca.gestionchalet.domain.RecuRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Instancié par Spring, qui lui injecte les règles configurées. */
public class AmountCentsValidator implements ConstraintValidator<AmountCents, Long> {

    private final RecuRules rules;
    private long min;

    public AmountCentsValidator(RecuRules rules) {
        this.rules = rules;
    }

    @Override
    public void initialize(AmountCents constraint) {
        min = constraint.min();
    }

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        return value == null || (value >= min && value <= rules.montantMaxCents());
    }
}
