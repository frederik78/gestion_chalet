package archfixtures.couches.repository;

/** Accès de la couche repository vers la couche security, soumis à TechnicalStructureTest. */
public class RepositoryVersSecurity {
    void appeler(archfixtures.couches.security.Cible cible) {
        cible.appeler();
    }
}
