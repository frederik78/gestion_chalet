package ca.gestionchalet.domain;

/** Raison pour laquelle la copie d'un reçu attend une action de l'utilisateur (PRD, section 4.2). */
public enum MotifBlocage {
    AUTORISATION_GOOGLE,
    /** Le fichier ou l'onglet est refusé par Google, ou n'est pas configuré. */
    FICHIER_INACCESSIBLE,
    /** L'année du reçu n'est pas dans la correspondance de la configuration (D-18) : les photos sont déposées, la ligne attend. */
    ANNEE_NON_CONFIGUREE,
    /** L'onglet n'a pas la structure attendue : un en-tête manque ou est en double, un mois a deux sections (R-24) ; rien n'est écrit tant que l'onglet n'est pas remis en état. */
    STRUCTURE_SHEET
}
