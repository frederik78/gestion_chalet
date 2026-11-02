package archfixtures.layers.repository;

/** Accès de la couche repository vers la couche security, soumis à TechnicalStructureTest. */
public class RepositoryToSecurity {
    void appeler(archfixtures.layers.security.Target cible) {
        cible.appeler();
    }
}
