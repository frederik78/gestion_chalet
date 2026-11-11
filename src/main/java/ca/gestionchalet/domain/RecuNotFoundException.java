package ca.gestionchalet.domain;

/** Aucun reçu n'a cet identifiant ; le message ne porte pas l'identifiant demandé. */
public class RecuNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecuNotFoundException() {
        super("Reçu introuvable");
    }
}
