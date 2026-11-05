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

    /** Parcours linéaire des deux bordures : une expression régulière ancrée aux deux bouts est quadratique sur une longue suite de séparateurs. */
    private static String trimSeparators(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSeparator(text.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isSeparator(char c) {
        int type = Character.getType(c);
        return type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                || c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    private static String comparableSupplier(String supplier) {
        String bordered = trimSeparators(supplier);
        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(bordered, Normalizer.Form.NFD)).replaceAll("");
        return withoutAccents.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)
                .replace("œ", "oe")
                .replace("æ", "ae")
                .replace('’', '\'');
    }
}
