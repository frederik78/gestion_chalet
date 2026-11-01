package archfixtures.regles.domain;

import jakarta.persistence.Entity;

/** Violation : le constructeur sans argument d'une entité est réservé à JPA, donc protected. */
@Entity
public class EntiteConstructeurPublic {
    public EntiteConstructeurPublic() {
    }
}
