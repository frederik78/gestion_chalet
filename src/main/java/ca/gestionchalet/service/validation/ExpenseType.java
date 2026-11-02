package ca.gestionchalet.service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Le texte est exactement un des types de dépense configurés ; l'absence relève de {@code @NotBlank}. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExpenseTypeValidator.class)
public @interface ExpenseType {

    String message() default "hors liste";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
