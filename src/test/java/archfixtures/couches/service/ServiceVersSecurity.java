package archfixtures.couches.service;

/** Accès de la couche service vers la couche security, soumis à TechnicalStructureTest. */
public class ServiceVersSecurity {
    void appeler(archfixtures.couches.security.Cible cible) {
        cible.appeler();
    }
}
