package archfixtures.layers.other;

/** Accès d'une classe hors couche vers la couche security, soumis à TechnicalStructureTest. */
public class OtherToSecurity {
    void appeler(archfixtures.layers.security.Target cible) {
        cible.appeler();
    }
}
