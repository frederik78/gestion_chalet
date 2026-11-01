package archfixtures.couches.service;

/** Accès de la couche service vers la couche repository, soumis à TechnicalStructureTest. */
public class ServiceVersRepository {
    void appeler(archfixtures.couches.repository.Cible cible) {
        cible.appeler();
    }
}
