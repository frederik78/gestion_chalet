package ca.gestionchalet.domain;

/** Un envoi compte plus de photos que la limite configurée ; son message ne porte aucune donnée de la requête. */
public class TooManyPhotosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TooManyPhotosException() {
        super("Trop de photos");
    }
}
