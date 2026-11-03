package ca.gestionchalet.domain;

/** Une photo déclare un format hors de la liste ; son message ne porte aucune donnée de la requête. */
public class UnsupportedPhotoFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedPhotoFormatException() {
        super("Format de photo non accepté");
    }
}
