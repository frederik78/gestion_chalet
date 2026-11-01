package archfixtures.regles.domain;

import jakarta.persistence.Entity;

/** Conforme : fabrique, constructeur protected, pas de setter. */
@Entity
public class EntiteConforme {
    protected EntiteConforme() {
    }

    public static EntiteConforme creer() {
        return new EntiteConforme();
    }
}
