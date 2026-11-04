package ca.gestionchalet.domain;

/** Un fichier porte la signature d'un format accepté, mais son en-tête n'est pas lisible ; son message ne porte aucune donnée de la requête. */
public class UnreadableImageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnreadableImageException() {
        super("Image illisible");
    }
}
