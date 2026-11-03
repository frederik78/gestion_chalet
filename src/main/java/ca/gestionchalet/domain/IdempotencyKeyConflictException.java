package ca.gestionchalet.domain;

/** Une clé d'idempotence connue revient avec un autre contenu (R-11) ; son message ne porte aucune valeur du reçu. */
public class IdempotencyKeyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException() {
        super("Clé d'idempotence déjà utilisée");
    }
}
