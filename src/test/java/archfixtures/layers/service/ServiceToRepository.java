package archfixtures.layers.service;

/** Accès de la couche service vers la couche repository, soumis à TechnicalStructureTest. */
public class ServiceToRepository {
    void appeler(archfixtures.layers.repository.Target cible) {
        cible.appeler();
    }
}
