package ca.gestionchalet.domain;

/** Une validation arrive sans aucune photo non vide (R-06). */
public class MissingPhotoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissingPhotoException() {
        super("Photo manquante");
    }
}
