package archfixtures.couches.web;

/** Accès de la couche web vers la couche security, soumis à TechnicalStructureTest. */
public class WebVersSecurity {
    void appeler(archfixtures.couches.security.Cible cible) {
        cible.appeler();
    }
}
