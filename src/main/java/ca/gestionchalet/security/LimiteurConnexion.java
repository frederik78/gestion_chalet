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
public final class LimiteurConnexion {

    private record Tentatives(int nombre, Instant derniere) {
    }

    private final int seuil;
    private final Duration dureeBlocage;
    private final int sourcesMax;
    private final Clock horloge;
    private final Map<String, Tentatives> parSource = new HashMap<>();

    public LimiteurConnexion(int seuil, Duration dureeBlocage, int sourcesMax, Clock horloge) {
        this.seuil = seuil;
        this.dureeBlocage = dureeBlocage;
        this.sourcesMax = sourcesMax;
        this.horloge = horloge;
    }

    /** Compte une tentative pour cette source. Si elle est bloquée, rien n'est compté et le temps restant est renvoyé. */
    public synchronized Optional<Duration> reserver(String source) {
        Instant maintenant = horloge.instant();
        parSource.values().removeIf(tentatives -> estPerimee(tentatives, maintenant));
        Tentatives tentatives = parSource.get(source);
        if (tentatives != null && tentatives.nombre() >= seuil) {
            return Optional.of(Duration.between(maintenant, tentatives.derniere().plus(dureeBlocage)));
        }
        if (tentatives == null && parSource.size() >= sourcesMax) {
            oublierLaPlusAncienne();
        }
        parSource.put(source, new Tentatives(tentatives == null ? 1 : tentatives.nombre() + 1, maintenant));
        return Optional.empty();
    }

    /** Une connexion réussie efface le compte de sa source. */
    public synchronized void reinitialiser(String source) {
        parSource.remove(source);
    }

    synchronized int sourcesSuivies() {
        return parSource.size();
    }

    private void oublierLaPlusAncienne() {
        parSource.entrySet().stream()
                .min(Comparator.comparing(entree -> entree.getValue().derniere()))
                .ifPresent(plusAncienne -> parSource.remove(plusAncienne.getKey()));
    }

    private boolean estPerimee(Tentatives tentatives, Instant maintenant) {
        return !tentatives.derniere().plus(dureeBlocage).isAfter(maintenant);
    }
}
