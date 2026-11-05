package ca.gestionchalet.client;

/** Panne passagère de Google ou du réseau (R-20) : rien n'est perdu, la copie sera réessayée. Aucun contenu de reçu ni de réponse n'y figure. */
public class TransientGoogleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransientGoogleException(String nature, Throwable cause) {
        super(nature, cause);
    }

    public TransientGoogleException(String nature) {
        super(nature);
    }
}
