package archfixtures.rules.web.rest;

import jakarta.persistence.EntityManager;

/** Violation : la couche web n'accède pas aux données. */
public class DataAccessResource {
    EntityManager gestionnaire;
}
