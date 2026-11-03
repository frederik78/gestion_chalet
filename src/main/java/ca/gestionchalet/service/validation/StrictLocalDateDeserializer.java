package ca.gestionchalet.service.validation;

import java.time.LocalDate;

/** Une date réelle, au format {@code AAAA-MM-JJ} seul (section 11.4). */
public final class StrictLocalDateDeserializer extends StrictTextDeserializer<LocalDate> {

    public StrictLocalDateDeserializer() {
        super(LocalDate.class, "\\d{4}-\\d{2}-\\d{2}", LocalDate::parse);
    }
}
