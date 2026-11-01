package ca.gestionchalet.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Placé avant la chaîne de sécurité : une requête rejetée par le pare-feu HTTP porte donc aussi les en-têtes. */
final class EnTetesSecuriteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest requete, ServletResponse reponse, FilterChain suite) throws IOException, ServletException {
        EnTetesSecurite.appliquer((HttpServletResponse) reponse);
        suite.doFilter(requete, reponse);
    }
}
