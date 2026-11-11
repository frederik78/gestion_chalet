package archfixtures.layers.service;

/** Accès de la couche service vers la couche security, soumis à TechnicalStructureTest. */
public class ServiceToSecurity {
    void appeler(archfixtures.layers.security.Target cible) {
        cible.appeler();
    }
}
