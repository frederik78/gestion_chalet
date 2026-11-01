package ca.gestionchalet.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Refus de la chaîne de sécurité, au format d'erreur de l'API. */
public final class ErreursSecurite implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Seule route ouverte sans session : son refus CSRF reste un refus CSRF. Partout ailleurs, l'absence de session prime. */
    private static final String ROUTE_CONNEXION = "/api/connexion";

    @Override
    public void commence(HttpServletRequest requete, HttpServletResponse reponse, AuthenticationException cause) throws IOException {
        ReponsesErreur.ecrire(reponse, CodeErreur.NON_AUTHENTIFIE);
    }

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse, AccessDeniedException cause) throws IOException {
        boolean sansSession = SecurityContextHolder.getContext().getAuthentication() == null;
        boolean connexion = ROUTE_CONNEXION.equals(requete.getRequestURI());
        ReponsesErreur.ecrire(reponse, sansSession && !connexion ? CodeErreur.NON_AUTHENTIFIE : CodeErreur.CSRF_INVALIDE);
    }
}
