package archfixtures.layers.service;

/** Accès de la couche service vers la couche web, soumis à TechnicalStructureTest. */
public class ServiceToWeb {
    void appeler(archfixtures.layers.web.Target cible) {
        cible.appeler();
    }
}
