package archfixtures.couches.repository;

/** Accès de la couche repository vers la couche domain, soumis à TechnicalStructureTest. */
public class RepositoryVersDomain {
    void appeler(archfixtures.couches.domain.Cible cible) {
        cible.appeler();
    }
}
