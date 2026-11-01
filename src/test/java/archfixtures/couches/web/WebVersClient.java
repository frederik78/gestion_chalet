package archfixtures.couches.web;

/** Accès de la couche web vers la couche client, soumis à TechnicalStructureTest. */
public class WebVersClient {
    void appeler(archfixtures.couches.client.Cible cible) {
        cible.appeler();
    }
}
