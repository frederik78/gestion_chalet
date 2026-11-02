package ca.gestionchalet.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de sécurité ({@code recus.securite.*}). Le condensé du mot de passe, lui, est lu par {@code DomainUserDetailsService}.
 *
 * @param echecsAvantBlocage nombre d'échecs de connexion d'une source avant son blocage
 * @param dureeBlocage durée du blocage d'une source
 * @param sourcesSuiviesMax nombre maximal de sources suivies en mémoire
 * @param corpsRequeteMax taille maximale d'un corps de requête hors multipart
 * @param cookieSecurise vrai hors poste local : les cookies portent {@code Secure}
 */
@Validated
@ConfigurationProperties("recus.securite")
public record SecuriteProperties(
        @Min(1) int echecsAvantBlocage,
        @NotNull Duration dureeBlocage,
        @Min(1) int sourcesSuiviesMax,
        @NotNull DataSize corpsRequeteMax,
        boolean cookieSecurise) {
}
