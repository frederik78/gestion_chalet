package ca.gestionchalet.domain;

/** Les dimensions annoncées par l'en-tête d'une image dépassent la limite configurée ; son message ne porte aucune donnée de la requête. */
public class ImageTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageTooLargeException() {
        super("Dimensions d'image excessives");
    }
}
