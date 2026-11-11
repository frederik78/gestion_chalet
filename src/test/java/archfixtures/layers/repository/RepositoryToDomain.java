package archfixtures.layers.repository;

/** Accès de la couche repository vers la couche domain, soumis à TechnicalStructureTest. */
public class RepositoryToDomain {
    void appeler(archfixtures.layers.domain.Target cible) {
        cible.appeler();
    }
}
