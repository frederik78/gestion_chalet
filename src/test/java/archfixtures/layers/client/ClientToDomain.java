package archfixtures.layers.client;

/** Accès de la couche client vers la couche domain, soumis à TechnicalStructureTest. */
public class ClientToDomain {
    void appeler(archfixtures.layers.domain.Target cible) {
        cible.appeler();
    }
}
