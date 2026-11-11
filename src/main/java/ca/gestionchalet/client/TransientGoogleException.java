package ca.gestionchalet.client;

import java.time.Duration;
import java.util.Optional;

/**
 * Panne passagère de Google ou du réseau (R-20) : rien n'est perdu, la copie sera réessayée. Aucun contenu de reçu ni de réponse n'y figure.
 * Google peut y joindre le délai qu'il demande d'attendre ({@code Retry-After}).
 */
public class TransientGoogleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Duration retryAfter;

    public TransientGoogleException(String nature, Throwable cause) {
        super(nature, cause);
        this.retryAfter = null;
    }

    public TransientGoogleException(String nature) {
        this(nature, (Duration) null);
    }

    public TransientGoogleException(String nature, Duration retryAfter) {
        super(nature);
        this.retryAfter = retryAfter;
    }

    /** Le délai demandé par Google avant un nouvel essai, s'il en a donné un lisible. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
