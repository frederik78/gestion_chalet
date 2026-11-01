package archfixtures.couches.web;

/** Accès de la couche web vers la couche repository, soumis à TechnicalStructureTest. */
public class WebVersRepository {
    void appeler(archfixtures.couches.repository.Cible cible) {
        cible.appeler();
    }
}
