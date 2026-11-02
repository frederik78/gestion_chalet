package ca.gestionchalet.config;

import ca.gestionchalet.repository.PhotoStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Transmet la configuration {@code recus.*} aux couches qui ne peuvent pas lire {@code config} (ADR 0001). */
@Configuration
public class RecuConfiguration {

    @Bean
    PhotoStorage photoStorage(ApplicationProperties properties) {
        return new PhotoStorage(properties.dossierPhotos());
    }
}
