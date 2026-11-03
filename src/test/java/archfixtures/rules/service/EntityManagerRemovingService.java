package archfixtures.rules.service;

import jakarta.persistence.EntityManager;

/** Violation volontaire : une suppression par {@code EntityManager}. */
public class EntityManagerRemovingService {

    public void purge(EntityManager manager, Object recu) {
        manager.remove(recu);
    }
}
