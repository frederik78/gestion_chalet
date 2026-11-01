package archfixtures.couches.service;

/** Accès de la couche service vers la couche client, soumis à TechnicalStructureTest. */
public class ServiceVersClient {
    void appeler(archfixtures.couches.client.Cible cible) {
        cible.appeler();
    }
}
