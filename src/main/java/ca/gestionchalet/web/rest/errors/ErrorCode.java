package ca.gestionchalet.web.rest.errors;

/** Codes d'erreur de l'API : statut HTTP et message générique, sans aucun détail technique. */
public enum ErrorCode {
    VALIDATION(400, "Certains champs sont invalides."),
    REQUETE_INVALIDE(400, "La requête est invalide."),
    NON_AUTHENTIFIE(401, "Authentification requise."),
    IDENTIFIANTS_INVALIDES(401, "Identifiants invalides."),
    CSRF_INVALIDE(403, "Jeton de sécurité absent ou invalide."),
    INTROUVABLE(404, "Ressource introuvable."),
    TAILLE_EXCESSIVE(413, "La requête est trop volumineuse."),
    FORMAT_NON_ACCEPTE(415, "Format non accepté."),
    TROP_DE_TENTATIVES(429, "Trop de tentatives. Réessayez plus tard."),
    ERREUR_INTERNE(500, "Une erreur interne est survenue.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** Le code d'une réponse d'erreur dont seul le statut est connu (framework, serveur). */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> NON_AUTHENTIFIE;
            case 403 -> CSRF_INVALIDE;
            case 404, 405 -> INTROUVABLE;
            case 413 -> TAILLE_EXCESSIVE;
            case 415 -> FORMAT_NON_ACCEPTE;
            case 429 -> TROP_DE_TENTATIVES;
            default -> status >= 400 && status < 500 ? REQUETE_INVALIDE : ERREUR_INTERNE;
        };
    }
}
