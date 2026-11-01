package ca.gestionchalet.config;

import jakarta.servlet.http.HttpServletResponse;

/** Les en-têtes de sécurité de toute réponse, posés par le filtre de l'application comme par la valve de Tomcat. */
final class EnTetesSecurite {

    private static final String POLITIQUE_CONTENU = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "connect-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    private EnTetesSecurite() {
    }

    static void appliquer(HttpServletResponse reponse) {
        reponse.setHeader("Content-Security-Policy", POLITIQUE_CONTENU);
        reponse.setHeader("X-Content-Type-Options", "nosniff");
        reponse.setHeader("X-Frame-Options", "DENY");
        reponse.setHeader("Referrer-Policy", "no-referrer");
        reponse.setHeader("Cache-Control", "no-store");
    }
}
