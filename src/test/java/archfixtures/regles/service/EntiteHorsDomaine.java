package archfixtures.regles.service;

import jakarta.persistence.Entity;

/** Violation : une entité réside dans domain/. */
@Entity
public class EntiteHorsDomaine {
    protected EntiteHorsDomaine() {
    }
}
