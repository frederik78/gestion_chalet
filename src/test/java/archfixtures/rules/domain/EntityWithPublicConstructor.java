package archfixtures.rules.domain;

import jakarta.persistence.Entity;

/** Violation : le constructeur sans argument d'une entité est réservé à JPA, donc protected. */
@Entity
public class EntityWithPublicConstructor {
    public EntityWithPublicConstructor() {
    }
}
