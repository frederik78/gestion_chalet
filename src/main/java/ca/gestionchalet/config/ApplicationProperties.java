package ca.gestionchalet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de l'application ({@code recus.*}), comme le {@code ApplicationProperties} de JHipster, à un écart près : les clés
 * inconnues restent permises, car {@code recus.modele} et le condensé du mot de passe sont lus ailleurs.
 *
 * @param securite réglages de sécurité ({@code recus.securite.*})
 * @param google réglages de l'autorisation Google ({@code recus.google.*})
 * @param copie réglages de la copie vers Drive et le Sheet ({@code recus.copie.*})
 * @param dossierPhotos dossier des photos, hors de la base et de la racine web
 * @param typesDepense liste fermée des types de dépense, dans l'orthographe du Sheet
 * @param photosParRecuMax nombre maximal de photos d'un reçu
 * @param formats types de média acceptés pour une photo
 * @param dimensionsMaxMegapixels dimensions maximales d'une image, en mégapixels
 * @param fuseauHoraire fuseau de la date du jour, à laquelle une date de reçu ne peut être postérieure
 * @param alertes seuils des alertes ({@code recus.alertes.*})
 * @param lecture réglages de la lecture par le modèle ({@code recus.lecture.*})
 */
@Validated
@ConfigurationProperties("recus")
public record ApplicationProperties(@Valid @NotNull Security securite, @Valid @NotNull Google google, @Valid @NotNull Copy copie, @NotNull Path dossierPhotos,
        @NotEmpty List<String> typesDepense,
        @Min(1) int photosParRecuMax,
        @NotEmpty List<String> formats,
        @Min(1) long dimensionsMaxMegapixels,
        @NotNull ZoneId fuseauHoraire,
        @Valid @NotNull Alerts alertes,
        @Valid @NotNull Reading lecture) {

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
     * La clé de chiffrement et les identifiants du client, secrets, sont lus à part : un record les écrirait dans son {@code toString}.
     *
     * @param delaiAppel délai maximal d'un appel à Google pour obtenir un jeton
     * @param adresseConsentement adresse du consentement Google
     * @param adresseJeton adresse des jetons Google
     * @param adresseRappel adresse de rappel
     * @param droits droits demandés (PRD, section 7.6)
     */
    public record Google(@NotNull Duration delaiAppel, @NotBlank String adresseConsentement, @NotBlank String adresseJeton,
            @NotBlank String adresseRappel, @NotEmpty List<String> droits) {

        public Google {
            droits = droits == null ? List.of() : List.copyOf(droits);
        }
    }

    /**
     * @param automatique vrai : un fil copie les reçus validés, dès qu'ils le sont puis à chaque passage périodique ; faux : seul un appel direct copie
     * @param intervalle attente entre deux passages périodiques
     * @param delaiAppel délai maximal d'un appel à Drive ou à Sheets
     * @param dossierDrive nom du dossier que l'application crée dans Drive (R-28)
     * @param annees le fichier et l'onglet du Sheet de chaque année, clés = année de la date du reçu (D-18) ; sans entrée, ou avec une entrée dont le fichier ou l'onglet est vide, aucune copie ne part
     * @param adresseDrive adresse de l'API de Drive
     * @param adresseSheets adresse de l'API de Sheets
     * @param adresseLienFichier adresse d'un fichier Drive, où {@code {id}} est l'identifiant renvoyé par Drive
     */
    public record Copy(boolean automatique, @NotNull Duration intervalle, @NotNull Duration delaiAppel, @NotBlank String dossierDrive,
            Map<Integer, YearSheet> annees, @NotBlank String adresseDrive, @NotBlank String adresseSheets, @NotBlank String adresseLienFichier) {

        public Copy {
            annees = annees == null ? Map.of() : Map.copyOf(annees);
        }
    }

    /**
     * @param fichier identifiant du fichier du Sheet de l'année
     * @param onglet titre exact de l'onglet de l'année où s'écrivent les lignes
     */
    public record YearSheet(String fichier, String onglet) {
    }

    /**
     * Le délai de lecture, {@code recus.lecture.delai}, est transmis au client du modèle par {@code spring.ai.anthropic.timeout}.
     *
     * @param lecturesParHeure nombre maximal de lectures sur une heure glissante
     * @param coteMaxPixels long côté de la copie envoyée au modèle
     * @param coteSourceMaxPixels long côté au-delà duquel une photo n'est pas décodée
     * @param decodagesSimultanes nombre de photos décodées en même temps
     * @param passesDecodageMax nombre de passes d'un décodage
     * @param delaiDecodage durée maximale d'un décodage
     * @param attenteDecodage attente maximale d'une place de décodage
     */
    public record Reading(@Min(1) int lecturesParHeure, @Min(1) int coteMaxPixels, @Min(1) int coteSourceMaxPixels, @Min(1) int decodagesSimultanes,
            @Min(1) int passesDecodageMax, @NotNull Duration delaiDecodage, @NotNull Duration attenteDecodage) {
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
