package ca.gestionchalet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de l'application ({@code recus.*}), comme le {@code ApplicationProperties} de JHipster, à un écart près : les clés
 * inconnues restent permises, car {@code recus.modele} et le condensé du mot de passe sont lus ailleurs.
 *
 * @param securite réglages de sécurité ({@code recus.securite.*})
 * @param dossierPhotos dossier des photos, hors de la base et de la racine web
 * @param typesDepense liste fermée des types de dépense, dans l'orthographe du Sheet
 * @param photosParRecuMax nombre maximal de photos d'un reçu
 * @param formats types de média acceptés pour une photo
 * @param dimensionsMaxMegapixels dimensions maximales d'une image, en mégapixels
 * @param fuseauHoraire fuseau de la date du jour, à laquelle une date de reçu ne peut être postérieure
 * @param alertes seuils des alertes ({@code recus.alertes.*})
 */
@Validated
@ConfigurationProperties("recus")
public record ApplicationProperties(@Valid @NotNull Security securite, @NotNull Path dossierPhotos,
        @NotEmpty List<String> typesDepense,
        @Min(1) int photosParRecuMax,
        @NotEmpty List<String> formats,
        @Min(1) long dimensionsMaxMegapixels,
        @NotNull ZoneId fuseauHoraire,
        @Valid @NotNull Alerts alertes) {

    public ApplicationProperties {
        typesDepense = typesDepense == null ? List.of() : List.copyOf(typesDepense);
        formats = formats == null ? List.of() : List.copyOf(formats);
    }

    /**
     * @param seuilNumerosInscriptionCents montant de référence à partir duquel les numéros d'inscription sont exigés
     * @param seuilPieceAVerifierCents montant de référence à partir duquel la pièce est à vérifier
     * @param ecartTolereCents écart de calcul au-delà duquel l'alerte est levée
     */
    public record Alerts(@Min(1) long seuilNumerosInscriptionCents, @Min(1) long seuilPieceAVerifierCents, @Min(0) long ecartTolereCents) {
    }

    /**
     * Le condensé du mot de passe, lui, est lu par {@code DomainUserDetailsService}.
     *
     * @param echecsAvantBlocage nombre d'échecs de connexion d'une source avant son blocage
     * @param dureeBlocage durée du blocage d'une source
     * @param sourcesSuiviesMax nombre maximal de sources suivies en mémoire
     * @param corpsRequeteMax taille maximale d'un corps de requête hors multipart
     * @param cookieSecurise vrai hors poste local : les cookies portent {@code Secure}
     */
    public record Security(
            @Min(1) int echecsAvantBlocage,
            @NotNull Duration dureeBlocage,
            @Min(1) int sourcesSuiviesMax,
            @NotNull DataSize corpsRequeteMax,
            boolean cookieSecurise) {
    }
}
