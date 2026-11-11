package archfixtures.rules.service;

import org.springframework.jdbc.core.JdbcTemplate;

/** Violation volontaire : du SQL direct, hors de la liste des classes autorisées. */
public class JdbcPurgingService {

    public void purge(JdbcTemplate base) {
        base.update("delete from recu");
    }
}
