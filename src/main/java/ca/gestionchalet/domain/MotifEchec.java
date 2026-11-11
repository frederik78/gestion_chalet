package ca.gestionchalet.domain;

/** Pourquoi la dernière régénération a échoué (PRD, section 4.2). */
public enum MotifEchec {
    AUTORISATION_GOOGLE,
    FICHIER_INACCESSIBLE,
    /** Un onglet du nom de l'onglet généré existe sans avoir été créé par l'application (R-41). */
    ONGLET_NON_RECONNU,
    GOOGLE_INDISPONIBLE
}
