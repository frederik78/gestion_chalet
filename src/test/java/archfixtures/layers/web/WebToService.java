package archfixtures.layers.web;

/** Accès de la couche web vers la couche service, soumis à TechnicalStructureTest. */
public class WebToService {
    void appeler(archfixtures.layers.service.Target cible) {
        cible.appeler();
    }
}
