package ca.gestionchalet.client;

/** Le modèle n'a pas donné de réponse exploitable. Le message ne nomme que la nature de la panne, jamais le contenu de la réponse. */
public class RecuExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecuExtractionException(String nature) {
        super(nature);
    }
}
