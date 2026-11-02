package ca.gestionchalet.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Compte les tentatives de connexion par source et bloque une source au-delà du seuil. Une tentative est comptée avant la
 * vérification du mot de passe, en une seule opération : des requêtes simultanées ne peuvent donc pas dépasser le seuil.
 * Mémoire bornée, perdue au redémarrage.
 */
public final class LoginRateLimiter {

    private record Attempts(int count, Instant last) {
    }

    private final int threshold;
    private final Duration blockDuration;
    private final int maxSources;
    private final Clock clock;
    private final Map<String, Attempts> bySource = new HashMap<>();

    public LoginRateLimiter(int threshold, Duration blockDuration, int maxSources, Clock clock) {
        this.threshold = threshold;
        this.blockDuration = blockDuration;
        this.maxSources = maxSources;
        this.clock = clock;
    }

    /** Compte une tentative pour cette source. Si elle est bloquée, rien n'est compté et le temps restant est renvoyé. */
    public synchronized Optional<Duration> reserve(String source) {
        Instant now = clock.instant();
        bySource.values().removeIf(attempts -> isExpired(attempts, now));
        Attempts attempts = bySource.get(source);
        if (attempts != null && attempts.count() >= threshold) {
            return Optional.of(Duration.between(now, attempts.last().plus(blockDuration)));
        }
        if (attempts == null && bySource.size() >= maxSources) {
            forgetOldest();
        }
        bySource.put(source, new Attempts(attempts == null ? 1 : attempts.count() + 1, now));
        return Optional.empty();
    }

    /** Une connexion réussie efface le compte de sa source. */
    public synchronized void reset(String source) {
        bySource.remove(source);
    }

    synchronized int trackedSources() {
        return bySource.size();
    }

    private void forgetOldest() {
        bySource.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().last()))
                .ifPresent(oldest -> bySource.remove(oldest.getKey()));
    }

    private boolean isExpired(Attempts attempts, Instant now) {
        return !attempts.last().plus(blockDuration).isAfter(now);
    }
}
