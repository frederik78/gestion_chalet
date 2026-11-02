package ca.gestionchalet.service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Le montant, s'il est présent, va de {@link #min()} au maximum configuré ; {@code @Max} exigerait une constante. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AmountCentsValidator.class)
public @interface AmountCents {

    long min() default 0;

    String message() default "hors bornes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
