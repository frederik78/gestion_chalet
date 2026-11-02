package archfixtures.rules.web.rest;

import archfixtures.rules.domain.CompliantEntity;

/** Violation : une entité n'est jamais renvoyée en JSON. */
public class SerializesEntityResource {
    public CompliantEntity lire() {
        return CompliantEntity.creer();
    }
}
