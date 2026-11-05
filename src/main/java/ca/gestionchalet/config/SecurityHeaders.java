package ca.gestionchalet.config;

import jakarta.servlet.http.HttpServletResponse;

/** Les en-têtes de sécurité de toute réponse, posés par le filtre de l'application comme par la valve de Tomcat. */
final class SecurityHeaders {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "connect-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    private SecurityHeaders() {
    }

    static void apply(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
    }
}
