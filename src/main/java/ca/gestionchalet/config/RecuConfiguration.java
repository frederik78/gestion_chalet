package ca.gestionchalet.config;

import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.repository.PhotoStorage;
import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Transmet la configuration {@code recus.*} aux couches qui ne peuvent pas lire {@code config} (ADR 0001). */
@Configuration
public class RecuConfiguration {

    @Bean
    PhotoStorage photoStorage(ApplicationProperties properties) {
        return new PhotoStorage(properties.dossierPhotos());
    }

    @Bean
    RecuRules recuRules(ApplicationProperties properties) {
        return new RecuRules(properties.typesDepense(), properties.montantMaxCents());
    }

    /** {@code @PastOrPresent} compare à la date du jour dans ce fuseau, et non dans celui de la machine. */
    @Bean
    ValidationConfigurationCustomizer validationClock(ApplicationProperties properties, Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock.withZone(properties.fuseauHoraire()));
    }
}
