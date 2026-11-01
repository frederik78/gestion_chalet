package archfixtures.regles.domain;

import jakarta.persistence.Entity;

/** Violation : setter public sur une entité. */
@Entity
public class EntiteAvecSetter {
    private String valeur;

    protected EntiteAvecSetter() {
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }
}
