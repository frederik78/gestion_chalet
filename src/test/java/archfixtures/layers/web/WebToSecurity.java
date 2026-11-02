package archfixtures.layers.web;

/** Accès de la couche web vers la couche security, soumis à TechnicalStructureTest. */
public class WebToSecurity {
    void appeler(archfixtures.layers.security.Target cible) {
        cible.appeler();
    }
}
