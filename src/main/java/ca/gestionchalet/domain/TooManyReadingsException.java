package ca.gestionchalet.domain;

import java.time.Duration;

/** La limite de lectures est atteinte (CS19). */
public class TooManyReadingsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public TooManyReadingsException(Duration retryAfter) {
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
