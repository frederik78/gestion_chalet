package archfixtures.couches.client;

/** Accès de la couche client vers la couche domain, soumis à TechnicalStructureTest. */
public class ClientVersDomain {
    void appeler(archfixtures.couches.domain.Cible cible) {
        cible.appeler();
    }
}
