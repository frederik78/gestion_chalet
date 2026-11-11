package ca.gestionchalet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

/** Le profil {@code dev} assouplit la sécurité (cookies non {@code Secure}) : il se signale à chaque démarrage, sans journaliser aucune valeur de configuration. */
@Component
@Profile("dev")
class DevelopmentModeWarning {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentModeWarning.class);

    @EventListener(ApplicationReadyEvent.class)
    void warn() {
        LOG.warn("MODE DEV ACTIF : cookies non sécurisés (HTTP accepté). Ne jamais l'utiliser en production.");
    }
}
