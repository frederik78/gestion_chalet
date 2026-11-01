package archfixtures.couches.web;

/** Accès de la couche web vers la couche config, soumis à TechnicalStructureTest. */
public class WebVersConfig {
    void appeler(archfixtures.couches.config.Cible cible) {
        cible.appeler();
    }
}
