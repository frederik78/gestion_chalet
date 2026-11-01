package ca.gestionchalet.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de sécurité ({@code recus.securite.*}). Le condensé du mot de passe vient de l'environnement ; il reste
 * nullable ici pour que le compte local refuse lui-même le démarrage avec son propre message.
 *
 * @param condenseMotDePasse condensé bcrypt du mot de passe du compte local
 * @param echecsAvantBlocage nombre d'échecs de connexion d'une source avant son blocage
 * @param dureeBlocage durée du blocage d'une source
 * @param sourcesSuiviesMax nombre maximal de sources suivies en mémoire
 * @param inactiviteSession durée d'inactivité au-delà de laquelle une session expire
 * @param cookieSecurise vrai hors poste local : les cookies portent {@code Secure}
 */
@Validated
@ConfigurationProperties("recus.securite")
public record SecuriteProperties(
        String condenseMotDePasse,
        @Min(1) int echecsAvantBlocage,
        @NotNull Duration dureeBlocage,
        @Min(1) int sourcesSuiviesMax,
        @NotNull Duration inactiviteSession,
        boolean cookieSecurise) {
}
