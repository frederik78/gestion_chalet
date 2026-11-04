package ca.gestionchalet.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Calcule les alertes d'un reçu, à partir de seuils tenus en configuration ({@code recus.alertes.*}) et jamais en constantes. Rien ici ne lit
 * d'alerte venue du client ni du modèle ; le résultat ne bloque jamais l'enregistrement. Tout est en cents entiers.
 *
 * @param seuilNumerosInscriptionCents montant de référence à partir duquel les numéros d'inscription sont exigés
 * @param seuilPieceAVerifierCents montant de référence à partir duquel la pièce est à vérifier
 * @param ecartTolereCents écart de calcul au-delà duquel l'alerte est levée
 */
public record AlertRules(long seuilNumerosInscriptionCents, long seuilPieceAVerifierCents, long ecartTolereCents) {

    private static final long CENTS_PAR_DOLLAR = 100L;

    /**
     * @param montantTotalLuCents total lu par le modèle, s'il existe
     * @param possibleDuplicate vrai si un autre reçu a le même fournisseur, la même date et le même total, ce que seul l'appelant peut savoir
     * @return les alertes, dans l'ordre du tableau du PRD
     */
    public List<Alerte> evaluate(Valeurs valeurs, Long montantTotalLuCents, boolean possibleDuplicate) {
        long reference = montantTotalLuCents == null ? valeurs.montantTotalCents() : Math.max(valeurs.montantTotalCents(), montantTotalLuCents);
        List<Alerte> alertes = new ArrayList<>();
        if (reference >= seuilNumerosInscriptionCents && (valeurs.numeroInscriptionTps() == null || valeurs.numeroInscriptionTvq() == null)) {
            alertes.add(new Alerte(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, missingNumbersText(valeurs), null));
        }
        if (reference >= seuilPieceAVerifierCents) {
            alertes.add(new Alerte(AlerteCode.PIECE_A_VERIFIER, "Pièce à vérifier : nom de l'acheteur, description, conditions de paiement", null));
        }
        if (valeurs.montantAvantTaxesCents() != null) {
            long ecart = Math.abs(valeurs.montantAvantTaxesCents() + zeroIfAbsent(valeurs.tpsCents()) + zeroIfAbsent(valeurs.tvqCents()) - valeurs.montantTotalCents());
            if (ecart > ecartTolereCents) {
                alertes.add(new Alerte(AlerteCode.ECART_CALCUL, "Écart de calcul : " + dollars(ecart), ecart));
            }
        }
        if (possibleDuplicate) {
            alertes.add(new Alerte(AlerteCode.DOUBLON_POSSIBLE, "Doublon possible", null));
        }
        return List.copyOf(alertes);
    }

    private static String missingNumbersText(Valeurs valeurs) {
        StringJoiner manquants = new StringJoiner(", ", "Numéros d'inscription manquants : ", "");
        if (valeurs.numeroInscriptionTps() == null) {
            manquants.add("TPS");
        }
        if (valeurs.numeroInscriptionTvq() == null) {
            manquants.add("TVQ");
        }
        return manquants.toString();
    }

    /** Pour cette seule comparaison (R-14) : la taxe du reçu reste vide. */
    private static long zeroIfAbsent(Long taxe) {
        return taxe == null ? 0L : taxe;
    }

    private static String dollars(long cents) {
        return String.format(Locale.ROOT, "%d,%02d $", cents / CENTS_PAR_DOLLAR, cents % CENTS_PAR_DOLLAR);
    }
}
