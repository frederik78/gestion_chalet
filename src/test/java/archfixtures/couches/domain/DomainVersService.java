package archfixtures.couches.domain;

/** Accès de la couche domain vers la couche service, soumis à TechnicalStructureTest. */
public class DomainVersService {
    void appeler(archfixtures.couches.service.Cible cible) {
        cible.appeler();
    }
}
