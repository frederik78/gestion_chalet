package ca.gestionchalet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Placé avant la chaîne de sécurité : une requête rejetée par le pare-feu HTTP porte donc aussi les en-têtes. */
final class EnTetesSecuriteFilter extends OncePerRequestFilter {

    /** Une requête refusée par Tomcat (TRACE) n'atteint les filtres qu'au renvoi vers la page d'erreur : ce passage est donc le premier. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws IOException, ServletException {
        EnTetesSecurite.appliquer(reponse);
        suite.doFilter(requete, reponse);
    }
}
