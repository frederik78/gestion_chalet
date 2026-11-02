package archfixtures.rules.domain;

import jakarta.persistence.Column;

/** Conforme : les annotations jakarta.persistence sont permises dans le domaine. */
public class DomainWithPersistence {
    @Column
    String valeur;
}
