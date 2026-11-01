package archfixtures.couches.config;

/** Accès de la couche config vers la couche web, soumis à TechnicalStructureTest. */
public class ConfigVersWeb {
    void appeler(archfixtures.couches.web.Cible cible) {
        cible.appeler();
    }
}
