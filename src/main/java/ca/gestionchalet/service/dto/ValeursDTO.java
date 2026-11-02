package ca.gestionchalet.service.dto;

import ca.gestionchalet.service.validation.AmountCents;
import ca.gestionchalet.service.validation.ExpenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Objet {@code valeurs} du contrat (PRD, sections 7.1 et 11.4). */
public record ValeursDTO(
        @NotNull @PastOrPresent LocalDate date,
        @NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL_CHARACTER) String fournisseur,
        @Size(max = 200) @Pattern(regexp = NO_CONTROL_CHARACTER) String objet,
        @NotBlank @ExpenseType String typeDepense,
        @AmountCents Long montantAvantTaxesCents,
        @AmountCents Long tpsCents,
        @AmountCents Long tvqCents,
        @NotNull @AmountCents(min = 1) Long montantTotalCents,
        @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTps,
        @Size(max = 30) @Pattern(regexp = REGISTRATION_NUMBER) String numeroInscriptionTvq) {

    private static final String NO_CONTROL_CHARACTER = "\\P{Cc}*";
    private static final String REGISTRATION_NUMBER = "[\\p{L}\\p{N} -]*";

    public ValeursDTO {
        fournisseur = fournisseur == null ? null : fournisseur.strip();
    }
}
