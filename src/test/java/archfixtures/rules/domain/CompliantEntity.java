package archfixtures.rules.domain;

import jakarta.persistence.Entity;

/** Conforme : fabrique, constructeur protected, pas de setter. */
@Entity
public class CompliantEntity {
    protected CompliantEntity() {
    }

    public static CompliantEntity creer() {
        return new CompliantEntity();
    }
}
