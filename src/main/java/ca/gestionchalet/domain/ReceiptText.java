package ca.gestionchalet.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Règles uniques de texte d'un reçu : ce qui est vide, la forme enregistrée et la comparaison de deux fournisseurs (R-15, D-61).
 * Un texte fait seulement de séparateurs {@code \p{Z}} (espace insécable compris) est vide : {@code strip} ne retire pas l'espace insécable.
 * Limite : U+3164 et U+2800 paraissent vides mais sont pour Unicode une lettre et un symbole ordinaires ; ils restent acceptés.
 */
public final class ReceiptText {

    private static final Pattern ONLY_SEPARATORS = Pattern.compile("\\p{Z}*");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$");

    private ReceiptText() {
    }

    /** Vrai pour un texte absent, vide ou fait seulement de séparateurs. */
    public static boolean isEmpty(String text) {
        return text == null || normalize(text).isEmpty();
    }

    /**
     * Forme enregistrée : espaces retirés en bordure, ou texte vide si rien d'autre que des séparateurs. Un espace insécable en bordure d'un
     * texte qui a du contenu reste en place : la valeur enregistrée est celle que l'utilisateur a validée.
     */
    public static String normalize(String text) {
        String stripped = text.strip();
        return ONLY_SEPARATORS.matcher(stripped).matches() ? "" : stripped;
    }

    /**
     * Même fournisseur, sans tenir compte de la casse, des accents, de la ligature (œ = oe, æ = ae), de l'apostrophe courbe ni des
     * séparateurs en bordure. Rien d'autre : ni ponctuation, ni séparateur du milieu, ni « Inc. » contre « Inc ».
     */
    public static boolean sameSupplier(String first, String second) {
        return comparableSupplier(first).equals(comparableSupplier(second));
    }

    private static String comparableSupplier(String supplier) {
        String bordered = EDGE_SEPARATORS.matcher(supplier).replaceAll("");
        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(bordered, Normalizer.Form.NFD)).replaceAll("");
        return withoutAccents.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)
                .replace("œ", "oe")
                .replace("æ", "ae")
                .replace('’', '\'');
    }
}
