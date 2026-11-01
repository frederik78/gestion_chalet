package archfixtures.couches.client;

/** Accès de la couche client vers la couche service, soumis à TechnicalStructureTest. */
public class ClientVersService {
    void appeler(archfixtures.couches.service.Cible cible) {
        cible.appeler();
    }
}
