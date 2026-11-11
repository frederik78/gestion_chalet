package ca.gestionchalet.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Le rythme des essais d'un dépôt de photo (R-20) : après le premier échec, {@code initial} ; l'attente double à chaque échec de plus, sans dépasser {@code maximum} ; sans limite de
 * tentatives. Un délai demandé par Google ({@code Retry-After}) ne raccourcit jamais l'attente, et ne la rallonge pas au-delà du plafond.
 *
 * @param initial attente après le premier échec
 * @param maximum plafond de l'attente
 */
public record DepositRetryRules(Duration initial, Duration maximum) {

    /** @param failures nombre d'échecs consécutifs, au moins 1 */
    public Duration delayAfter(int failures, Optional<Duration> requestedByGoogle) {
        Duration backoff = initial;
        for (int i = 1; i < failures && backoff.compareTo(maximum) < 0; i++) {
            backoff = backoff.multipliedBy(2);
        }
        Duration wait = backoff;
        if (requestedByGoogle.isPresent() && requestedByGoogle.get().compareTo(wait) > 0) {
            wait = requestedByGoogle.get();
        }
        return wait.compareTo(maximum) > 0 ? maximum : wait;
    }
}
