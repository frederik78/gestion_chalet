package archfixtures.layers.service;

/** Accès de la couche service vers la couche client, soumis à TechnicalStructureTest. */
public class ServiceToClient {
    void appeler(archfixtures.layers.client.Target cible) {
        cible.appeler();
    }
}
