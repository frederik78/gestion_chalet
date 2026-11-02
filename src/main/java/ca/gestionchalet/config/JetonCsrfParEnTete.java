package ca.gestionchalet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * Le jeton CSRF se lit seulement dans l'en-tête {@code X-XSRF-TOKEN}, jamais dans un paramètre : le corps d'une requête
 * n'est donc pas analysé avant le contrôle CSRF. Le jeton est chargé à chaque requête afin que son cookie soit posé dès la
 * page de connexion. Gardé face à {@code csrf.spa()} et au gestionnaire de JHipster : sans en-tête, ils lisent un paramètre, et
 * un formulaire au corps non terminé bloque alors le refus CSRF (mesuré : pas de réponse en 5 s, cs02).
 */
final class JetonCsrfParEnTete implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestAttributeHandler attribut = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse, Supplier<CsrfToken> jeton) {
        attribut.handle(requete, reponse, jeton);
        jeton.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest requete, CsrfToken jeton) {
        return requete.getHeader(jeton.getHeaderName());
    }
}
