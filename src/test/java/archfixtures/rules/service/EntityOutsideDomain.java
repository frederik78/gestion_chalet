package archfixtures.rules.service;

import jakarta.persistence.Entity;

/** Violation : une entité réside dans domain/. */
@Entity
public class EntityOutsideDomain {
    protected EntityOutsideDomain() {
    }
}
