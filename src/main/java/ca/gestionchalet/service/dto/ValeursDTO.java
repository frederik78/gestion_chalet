package ca.gestionchalet.service.dto;

import ca.gestionchalet.service.validation.ExpenseType;
import ca.gestionchalet.service.validation.StrictLocalDateDeserializer;
import ca.gestionchalet.service.validation.StrictStringDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Objet {@code valeurs} du contrat (PRD, sections 7.1 et 11.4). */
public record ValeursDTO(
        @NotNull @PastOrPresent @JsonDeserialize(using = StrictLocalDateDeserializer.class) LocalDate date,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL_OR_FORMAT_CHARACTER) String fournisseur,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 200) @Pattern(regexp = NO_CONTROL_OR_FORMAT_CHARACTER) String objet,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank @ExpenseType String typeDepense,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long montantAvantTaxesCents,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long tpsCents,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long tvqCents,
        @NotNull @Positive @Max(MONTANT_MAX_CENTS) Long montantTotalCents,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTps,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTvq) {

    /** 1 000 000,00 $ (R-10). */
    static final long MONTANT_MAX_CENTS = 100_000_000L;

    /** Ni contrôle ({@code Cc}), ni format ({@code Cf}) : espace de largeur nulle, inversion d'affichage. */
    private static final String NO_CONTROL_OR_FORMAT_CHARACTER = "[\\P{Cc}&&\\P{Cf}]*";
    private static final String REGISTRATION_NUMBER = "[\\p{L}\\p{N} -]*";

    public ValeursDTO {
        fournisseur = fournisseur == null ? null : fournisseur.strip();
        objet = stripToNull(objet);
        numeroInscriptionTps = stripToNull(numeroInscriptionTps);
        numeroInscriptionTvq = stripToNull(numeroInscriptionTvq);
    }

    /** Un texte facultatif sans contenu est absent : un numéro fait d'espaces ne passerait pas pour un numéro manquant. */
    private static String stripToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
