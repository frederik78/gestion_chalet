package archfixtures.couches.web;

/** Accès de la couche web vers la couche service, soumis à TechnicalStructureTest. */
public class WebVersService {
    void appeler(archfixtures.couches.service.Cible cible) {
        cible.appeler();
    }
}
