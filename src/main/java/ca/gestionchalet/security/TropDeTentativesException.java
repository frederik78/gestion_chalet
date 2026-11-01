package ca.gestionchalet.security;

import java.time.Duration;

/** La source est bloquée après trop d'échecs de connexion. */
public class TropDeTentativesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Duration restant;

    public TropDeTentativesException(Duration restant) {
        this.restant = restant;
    }

    public Duration restant() {
        return restant;
    }
}
