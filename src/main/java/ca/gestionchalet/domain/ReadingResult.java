package ca.gestionchalet.domain;

/** Issue d'une lecture (PRD, section 7.1) : calculée par le serveur, jamais lue du modèle. */
public enum ReadingResult {
    LU,
    ILLISIBLE,
    ECHEC
}
