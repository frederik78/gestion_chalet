package ca.gestionchalet.domain;

/** Une correction s'appuie sur une version du reçu qui n'est plus la courante (R-29) ; son message ne porte aucune valeur du reçu. */
public class StaleVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StaleVersionException() {
        super("Version du reçu périmée");
    }
}
