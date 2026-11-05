package ca.gestionchalet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Placé avant la chaîne de sécurité : une requête rejetée par le pare-feu HTTP porte donc aussi les en-têtes. Gardé face à
 * {@code .headers()}, qui s'exécute après ce pare-feu (mesuré : en-têtes absents des rejets, cs08, et pas de no-store exact, cs09).
 */
final class SecurityHeadersFilter extends OncePerRequestFilter {

    /** Une requête refusée par Tomcat (TRACE) n'atteint les filtres qu'au renvoi vers la page d'erreur : ce passage est donc le premier. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        SecurityHeaders.apply(response);
        chain.doFilter(request, response);
    }
}
