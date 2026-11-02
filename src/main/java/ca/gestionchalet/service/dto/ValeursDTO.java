package ca.gestionchalet.service.dto;

import java.time.LocalDate;

/** Objet {@code valeurs} du contrat (PRD, section 7.1). */
public record ValeursDTO(
        LocalDate date,
        String fournisseur,
        String objet,
        String typeDepense,
        Long montantAvantTaxesCents,
        Long tpsCents,
        Long tvqCents,
        Long montantTotalCents,
        String numeroInscriptionTps,
        String numeroInscriptionTvq) {
}
