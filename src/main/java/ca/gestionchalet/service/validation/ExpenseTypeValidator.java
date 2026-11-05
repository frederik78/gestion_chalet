package ca.gestionchalet.service.validation;

import ca.gestionchalet.domain.RecuRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Instancié par Spring, qui lui injecte les règles configurées. */
public class ExpenseTypeValidator implements ConstraintValidator<ExpenseType, String> {

    private final RecuRules rules;

    public ExpenseTypeValidator(RecuRules rules) {
        this.rules = rules;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || rules.typesDepense().contains(value);
    }
}
