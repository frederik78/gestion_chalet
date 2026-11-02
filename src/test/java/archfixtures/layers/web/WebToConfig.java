package archfixtures.layers.web;

/** Accès de la couche web vers la couche config, soumis à TechnicalStructureTest. */
public class WebToConfig {
    void appeler(archfixtures.layers.config.Target cible) {
        cible.appeler();
    }
}
