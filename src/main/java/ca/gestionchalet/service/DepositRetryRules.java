package ca.gestionchalet.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Le rythme des essais d'un dépôt de photo (R-20) : après le premier échec, {@code initial} ; l'attente double à chaque échec de plus, sans dépasser {@code maximum} ; sans limite de
 * tentatives. Un délai demandé par Google ({@code Retry-After}) ne raccourcit jamais l'attente, et ne la rallonge pas au-delà du plafond.
 *
 * @param initial attente après le premier échec
 * @param maximum plafond de l'attente
 */
public record DepositRetryRules(Duration initial, Duration maximum) {

    /** Le calcul du doublement est celui de Spring ({@code ExponentialBackOff}), plafonné à {@code maximum} ; la fusion avec {@code Retry-After} reste ici. */
    private ExponentialBackOff exponentialBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(initial.toMillis(), 2);
        backOff.setMaxInterval(maximum.toMillis());
        return backOff;
    }

    /** @param failures nombre d'échecs consécutifs, au moins 1 */
    public Duration delayAfter(int failures, Optional<Duration> requestedByGoogle) {
        BackOffExecution execution = exponentialBackOff().start();
        long millis = 0;
        for (int i = 0; i < failures; i++) {
            millis = execution.nextBackOff();
        }
        Duration wait = Duration.ofMillis(millis);
        if (requestedByGoogle.isPresent() && requestedByGoogle.get().compareTo(wait) > 0) {
            wait = requestedByGoogle.get();
        }
        return wait.compareTo(maximum) > 0 ? maximum : wait;
    }
}
