package ca.gestionchalet.client;

/** Le modèle n'a pas donné de réponse exploitable. Seule sa nature est conservée, jamais le contenu de la réponse. */
public class RecuExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Natures de panne, seules journalisées. */
    public enum Failure {
        /** Le service a répondu par une erreur (clé refusée, 5xx…). */
        SERVICE_ERROR,
        /** Réseau en panne ou délai dépassé. */
        UNREACHABLE,
        /** Réponse sans texte, ou qui n'est pas l'objet JSON attendu. */
        INVALID_RESPONSE
    }

    private final Failure failure;

    public RecuExtractionException(Failure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
