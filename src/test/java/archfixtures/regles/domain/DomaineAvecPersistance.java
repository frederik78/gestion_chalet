package archfixtures.regles.domain;

import jakarta.persistence.Column;

/** Conforme : les annotations jakarta.persistence sont permises dans le domaine. */
public class DomaineAvecPersistance {
    @Column
    String valeur;
}
