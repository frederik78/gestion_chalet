package ca.gestionchalet.domain;

/** Codes d'alerte, dans l'ordre du tableau du PRD : c'est celui des alertes d'un reçu et de la colonne NOTES (R-26). */
public enum AlerteCode {
    NUMEROS_INSCRIPTION_MANQUANTS,
    PIECE_A_VERIFIER,
    ECART_CALCUL,
    RAPPORT_TVQ_TPS,
    TPS_AU_DELA_DU_TAUX,
    DOUBLON_POSSIBLE
}
