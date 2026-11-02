package archfixtures.layers.domain;

/** Accès de la couche domain vers la couche service, soumis à TechnicalStructureTest. */
public class DomainToService {
    void appeler(archfixtures.layers.service.Target cible) {
        cible.appeler();
    }
}
