package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.LocalDateTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

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

    /** Même fournisseur au sens de {@link ReceiptText#sameSupplier} (R-15). */
    public boolean hasSameSupplier(Valeurs other) {
        return ReceiptText.sameSupplier(fournisseur, other.fournisseur);
    }

    /** Tous les champs, dans un ordre fixe : c'est le contenu que compare un renvoi (R-11). Une taxe absente y reste {@code null}. */
    public List<Object> contentFields() {
        return Arrays.asList(date, fournisseur, objet, typeDepense, montantAvantTaxesCents, tpsCents, tvqCents, montantTotalCents,
                numeroInscriptionTps, numeroInscriptionTvq);
    }
}
