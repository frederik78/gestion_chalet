package ca.gestionchalet.service.dto;

import ca.gestionchalet.service.validation.ExpenseType;
import ca.gestionchalet.service.validation.StrictLocalDateDeserializer;
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
        @NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL_CHARACTER) String fournisseur,
        @Size(max = 200) @Pattern(regexp = NO_CONTROL_CHARACTER) String objet,
        @NotBlank @ExpenseType String typeDepense,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long montantAvantTaxesCents,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long tpsCents,
        @PositiveOrZero @Max(MONTANT_MAX_CENTS) Long tvqCents,
        @NotNull @Positive @Max(MONTANT_MAX_CENTS) Long montantTotalCents,
        @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTps,
        @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTvq) {

    /** 1 000 000,00 $ (R-10). */
    static final long MONTANT_MAX_CENTS = 100_000_000L;

    private static final String NO_CONTROL_CHARACTER = "\\P{Cc}*";
    private static final String REGISTRATION_NUMBER = "[\\p{L}\\p{N} -]*";

    public ValeursDTO {
        fournisseur = fournisseur == null ? null : fournisseur.strip();
    }
}
