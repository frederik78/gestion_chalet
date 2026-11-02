package archfixtures.layers.web;

/** Accès de la couche web vers la couche client, soumis à TechnicalStructureTest. */
public class WebToClient {
    void appeler(archfixtures.layers.client.Target cible) {
        cible.appeler();
    }
}
