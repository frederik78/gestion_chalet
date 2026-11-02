package archfixtures.couches.autre;

/** Accès d'une classe hors couche vers la couche security, soumis à TechnicalStructureTest. */
public class AutreVersSecurity {
    void appeler(archfixtures.couches.security.Cible cible) {
        cible.appeler();
    }
}
