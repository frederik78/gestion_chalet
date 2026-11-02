package archfixtures.layers.web;

/** Accès de la couche web vers la couche repository, soumis à TechnicalStructureTest. */
public class WebToRepository {
    void appeler(archfixtures.layers.repository.Target cible) {
        cible.appeler();
    }
}
