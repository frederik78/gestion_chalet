package ca.gestionchalet.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Compte les échecs de connexion par source et bloque une source au-delà du seuil. Mémoire bornée, perdue au redémarrage. */
public final class LimiteurConnexion {

    private record Echecs(int nombre, Instant dernier) {
    }

    private final int seuil;
    private final Duration dureeBlocage;
    private final int sourcesMax;
    private final Clock horloge;
    private final Map<String, Echecs> parSource = new HashMap<>();

    public LimiteurConnexion(int seuil, Duration dureeBlocage, int sourcesMax, Clock horloge) {
        this.seuil = seuil;
        this.dureeBlocage = dureeBlocage;
        this.sourcesMax = sourcesMax;
        this.horloge = horloge;
    }

    /** Le temps de blocage restant pour cette source, vide si elle peut tenter sa chance. */
    public synchronized Optional<Duration> blocageRestant(String source) {
        Echecs echecs = parSource.get(source);
        if (echecs == null || echecs.nombre() < seuil) {
            return Optional.empty();
        }
        Duration restant = Duration.between(horloge.instant(), echecs.dernier().plus(dureeBlocage));
        return restant.isPositive() ? Optional.of(restant) : Optional.empty();
    }

    public synchronized void enregistrerEchec(String source) {
        Instant maintenant = horloge.instant();
        parSource.values().removeIf(echecs -> estPerime(echecs, maintenant));
        if (!parSource.containsKey(source) && parSource.size() >= sourcesMax) {
            parSource.entrySet().stream()
                    .min(Comparator.comparing(entree -> entree.getValue().dernier()))
                    .ifPresent(plusAncienne -> parSource.remove(plusAncienne.getKey()));
        }
        int nombre = parSource.containsKey(source) ? parSource.get(source).nombre() + 1 : 1;
        parSource.put(source, new Echecs(nombre, maintenant));
    }

    public synchronized void reinitialiser(String source) {
        parSource.remove(source);
    }

    synchronized int sourcesSuivies() {
        return parSource.size();
    }

    private boolean estPerime(Echecs echecs, Instant maintenant) {
        return !echecs.dernier().plus(dureeBlocage).isAfter(maintenant);
    }
}
