package archfixtures.regles.web.rest;

import archfixtures.regles.domain.EntiteConforme;

/** Violation : une entité n'est jamais renvoyée en JSON. */
public class SerialiseEntiteResource {
    public EntiteConforme lire() {
        return EntiteConforme.creer();
    }
}
