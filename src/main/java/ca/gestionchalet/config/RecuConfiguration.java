package ca.gestionchalet.config;

import ca.gestionchalet.domain.AlertRules;
import ca.gestionchalet.client.AnthropicRecuExtractor;
import ca.gestionchalet.client.RecuExtractor;
import ca.gestionchalet.domain.ReadingRules;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.security.ReadingRateLimiter;
import java.time.Clock;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Transmet la configuration {@code recus.*} aux couches qui ne peuvent pas lire {@code config} (ADR 0001). */
@Configuration
public class RecuConfiguration {

    @Bean
    RecuExtractor recuExtractor(ChatModel chatModel) {
        return new AnthropicRecuExtractor(chatModel);
    }

    @Bean
    PhotoStorage photoStorage(ApplicationProperties properties) {
        return new PhotoStorage(properties.dossierPhotos());
    }

    @Bean
    RecuRules recuRules(ApplicationProperties properties, MultipartProperties multipart) {
        return new RecuRules(properties.typesDepense(), properties.photosParRecuMax(),
                multipart.getMaxFileSize().toBytes(), properties.formats(), properties.dimensionsMaxMegapixels());
    }

    @Bean
    AlertRules alertRules(ApplicationProperties properties) {
        ApplicationProperties.Alerts alertes = properties.alertes();
        return new AlertRules(alertes.seuilNumerosInscriptionCents(), alertes.seuilPieceAVerifierCents(), alertes.ecartTolereCents());
    }

    @Bean
    ReadingRules readingRules(ApplicationProperties properties) {
        ApplicationProperties.Reading reading = properties.lecture();
        return new ReadingRules(reading.coteMaxPixels(), reading.coteSourceMaxPixels(), reading.decodagesSimultanes());
    }

    @Bean
    ReadingRateLimiter readingRateLimiter(ApplicationProperties properties, Clock clock) {
        return new ReadingRateLimiter(properties.lecture().lecturesParHeure(), clock);
    }

    /** {@code @PastOrPresent} compare à la date du jour dans ce fuseau, et non dans celui de la machine. */
    @Bean
    ValidationConfigurationCustomizer validationClock(ApplicationProperties properties, Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock.withZone(properties.fuseauHoraire()));
    }
}
