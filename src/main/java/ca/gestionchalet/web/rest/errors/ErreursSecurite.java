package ca.gestionchalet.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Refus de la chaîne de sécurité, au format d'erreur de l'API. */
public final class ErreursSecurite implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

    /** Seule route ouverte sans session : son refus CSRF reste un refus CSRF. Partout ailleurs, l'absence de session prime. */
    private final RequestMatcher connexion;

    public ErreursSecurite(RequestMatcher connexion) {
        this.connexion = connexion;
    }

    @Override
    public void commence(HttpServletRequest requete, HttpServletResponse reponse, AuthenticationException cause) throws IOException {
        ReponsesErreur.ecrire(reponse, CodeErreur.NON_AUTHENTIFIE);
    }

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse, AccessDeniedException cause) throws IOException {
        boolean sansSession = SecurityContextHolder.getContext().getAuthentication() == null;
        ReponsesErreur.ecrire(reponse, sansSession && !connexion.matches(requete) ? CodeErreur.NON_AUTHENTIFIE : CodeErreur.CSRF_INVALIDE);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest requete, HttpServletResponse reponse, AuthenticationException cause) throws IOException {
        ReponsesErreur.ecrire(reponse, CodeErreur.IDENTIFIANTS_INVALIDES);
    }
}
