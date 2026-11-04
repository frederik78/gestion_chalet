package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.LocalDateTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /** Même fournisseur, sans tenir compte de la casse, des accents ni des espaces en bordure (R-15). */
    public boolean hasSameSupplier(Valeurs other) {
        return comparableSupplier().equals(other.comparableSupplier());
    }

    private String comparableSupplier() {
        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(fournisseur.strip(), Normalizer.Form.NFD)).replaceAll("");
        return withoutAccents.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    /** Tous les champs, dans un ordre fixe : c'est le contenu que compare un renvoi (R-11). Une taxe absente y reste {@code null}. */
    public List<Object> contentFields() {
        return Arrays.asList(date, fournisseur, objet, typeDepense, montantAvantTaxesCents, tpsCents, tvqCents, montantTotalCents,
                numeroInscriptionTps, numeroInscriptionTvq);
    }
}
