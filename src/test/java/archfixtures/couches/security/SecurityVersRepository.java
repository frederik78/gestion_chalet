package archfixtures.couches.security;

/** Accès de la couche security vers la couche repository, soumis à TechnicalStructureTest. */
public class SecurityVersRepository {
    void appeler(archfixtures.couches.repository.Cible cible) {
        cible.appeler();
    }
}
