package archfixtures.layers.config;

/** Accès de la couche config vers la couche web, soumis à TechnicalStructureTest. */
public class ConfigToWeb {
    void appeler(archfixtures.layers.web.Target cible) {
        cible.appeler();
    }
}
