package ca.gestionchalet.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/** SQLite n'a pas de type horodatage : l'instant reste un texte ISO 8601 en UTC. */
@Converter
public class InstantTextConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    @Override
    public Instant convertToEntityAttribute(String texte) {
        return texte == null ? null : Instant.parse(texte);
    }
}
