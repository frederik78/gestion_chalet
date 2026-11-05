package archfixtures.layers.client;

/** Accès de la couche client vers la couche service, soumis à TechnicalStructureTest. */
public class ClientToService {
    void appeler(archfixtures.layers.service.Target cible) {
        cible.appeler();
    }
}
