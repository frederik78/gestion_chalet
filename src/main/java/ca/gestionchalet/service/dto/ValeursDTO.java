package ca.gestionchalet.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Objet {@code valeurs} du contrat (PRD, section 7.1). */
public record ValeursDTO(
        @NotNull LocalDate date,
        @NotBlank String fournisseur,
        String objet,
        @NotBlank String typeDepense,
        Long montantAvantTaxesCents,
        Long tpsCents,
        Long tvqCents,
        @NotNull Long montantTotalCents,
        String numeroInscriptionTps,
        String numeroInscriptionTvq) {

    public ValeursDTO {
        fournisseur = fournisseur == null ? null : fournisseur.strip();
    }
}
