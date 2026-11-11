package ca.gestionchalet.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;

/** SQLite n'a pas de type date : la date reste un texte {@code AAAA-MM-JJ}, sans dépendre du fuseau de la machine. */
@Converter
public class LocalDateTextConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String texte) {
        return texte == null ? null : LocalDate.parse(texte);
    }
}
