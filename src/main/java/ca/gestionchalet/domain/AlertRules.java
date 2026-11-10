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
 * @param tauxTpsMillipourcent taux de TPS du contrôle des taxes, en millièmes de pour cent (5 % = 5000)
 * @param tauxTvqMillipourcent taux de TVQ du contrôle des taxes, en millièmes de pour cent (9,975 % = 9975)
 * @param ecartTaxesTolereCents écart toléré par le contrôle des taxes (R-47, R-48), distinct de {@code ecartTolereCents}
 */
public record AlertRules(long seuilNumerosInscriptionCents, long seuilPieceAVerifierCents, long ecartTolereCents, long tauxTpsMillipourcent,
        long tauxTvqMillipourcent, long ecartTaxesTolereCents) {

    private static final long CENTS_PAR_DOLLAR = 100L;
    /** 100 % en millièmes de pour cent : met un montant et un taux dans la même unité sans diviser. */
    private static final long MILLIPOURCENT_PAR_UNITE = 100_000L;

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
        if (isTvqTpsRatioUnusual(valeurs.tpsCents(), valeurs.tvqCents())) {
            alertes.add(new Alerte(AlerteCode.RAPPORT_TVQ_TPS,
                    "Rapport TVQ / TPS inhabituel : TVQ " + dollars(valeurs.tvqCents()) + " pour une TPS de " + dollars(valeurs.tpsCents()), null));
        }
        if (isTpsAboveRate(valeurs.tpsCents(), valeurs.montantAvantTaxesCents())) {
            alertes.add(new Alerte(AlerteCode.TPS_AU_DELA_DU_TAUX,
                    "TPS supérieure à 5 % du montant avant taxes : " + dollars(valeurs.tpsCents()) + " pour " + dollars(valeurs.montantAvantTaxesCents()), null));
        }
        if (possibleDuplicate) {
            alertes.add(new Alerte(AlerteCode.DOUBLON_POSSIBLE, "Doublon possible", null));
        }
        return List.copyOf(alertes);
    }

    /** R-47 : |TVQ - TPS × taux TVQ / taux TPS| > tolérance, sans division : |taux TPS × TVQ - taux TVQ × TPS| > taux TPS × tolérance. */
    private boolean isTvqTpsRatioUnusual(Long tps, Long tvq) {
        if (tps == null || tvq == null || tps <= 0 || tvq <= 0) {
            return false;
        }
        return Math.abs(tauxTpsMillipourcent * tvq - tauxTvqMillipourcent * tps) > tauxTpsMillipourcent * ecartTaxesTolereCents;
    }

    /** R-48 : TPS > taux TPS × montant avant taxes + tolérance, ramené à des entiers en multipliant par 100 000. */
    private boolean isTpsAboveRate(Long tps, Long montantAvantTaxes) {
        if (tps == null || montantAvantTaxes == null) {
            return false;
        }
        return MILLIPOURCENT_PAR_UNITE * tps > tauxTpsMillipourcent * montantAvantTaxes + MILLIPOURCENT_PAR_UNITE * ecartTaxesTolereCents;
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
