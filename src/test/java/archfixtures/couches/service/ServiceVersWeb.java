package archfixtures.couches.service;

/** Accès de la couche service vers la couche web, soumis à TechnicalStructureTest. */
public class ServiceVersWeb {
    void appeler(archfixtures.couches.web.Cible cible) {
        cible.appeler();
    }
}
