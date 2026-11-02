package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.LocalDateTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/** Valeurs d'un reçu, telles que l'utilisateur les a validées ; une taxe absente reste {@code null}, distincte de 0. */
@Embeddable
public record Valeurs(
        @Column(name = "date_recu") @Convert(converter = LocalDateTextConverter.class) LocalDate date,
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
