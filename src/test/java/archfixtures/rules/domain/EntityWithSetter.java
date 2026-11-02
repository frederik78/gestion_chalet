package archfixtures.rules.domain;

import jakarta.persistence.Entity;

/** Violation : setter public sur une entité. */
@Entity
public class EntityWithSetter {
    private String valeur;

    protected EntityWithSetter() {
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }
}
