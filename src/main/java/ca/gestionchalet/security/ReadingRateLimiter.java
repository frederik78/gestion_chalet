package ca.gestionchalet.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Limite les lectures sur une heure glissante, pour maîtriser le coût du modèle (CS19). Une lecture est comptée avant l'appel, en une seule opération : des requêtes simultanées ne
 * peuvent pas dépasser la limite. Mémoire bornée par la limite, perdue au redémarrage.
 */
public final class ReadingRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int limit;
    private final Clock clock;
    private final Deque<Instant> readings = new ArrayDeque<>();

    public ReadingRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Compte une lecture. Si la limite est atteinte, rien n'est compté et le temps à attendre est renvoyé. */
    public synchronized Optional<Duration> reserve() {
        Instant now = clock.instant();
        Instant oldestKept = now.minus(WINDOW);
        while (!readings.isEmpty() && !readings.peekFirst().isAfter(oldestKept)) {
            readings.removeFirst();
        }
        if (readings.size() >= limit) {
            return Optional.of(Duration.between(oldestKept, readings.peekFirst()));
        }
        readings.addLast(now);
        return Optional.empty();
    }
}
