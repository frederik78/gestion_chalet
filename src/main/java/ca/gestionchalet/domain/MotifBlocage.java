package ca.gestionchalet.domain;

/** Raison pour laquelle la copie d'un reçu attend une action de l'utilisateur (PRD, section 4.2). */
public enum MotifBlocage {
    AUTORISATION_GOOGLE,
    /** Le fichier ou l'onglet est refusé par Google, ou n'est pas configuré. */
    FICHIER_INACCESSIBLE
}
