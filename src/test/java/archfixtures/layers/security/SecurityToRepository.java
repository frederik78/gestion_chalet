package archfixtures.layers.security;

/** Accès de la couche security vers la couche repository, soumis à TechnicalStructureTest. */
public class SecurityToRepository {
    void appeler(archfixtures.layers.repository.Target cible) {
        cible.appeler();
    }
}
