package ca.gestionchalet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Ouvre et ferme la session du compte local : nouvel identifiant à chaque ouverture, expiration par inactivité, jeton CSRF renouvelé. */
public final class SessionUtilisateur {

    private static final String COOKIE_SESSION = "JSESSIONID";
    private static final String PROPRIETAIRE = "proprietaire";

    private final SecurityContextRepository contextes;
    private final CsrfTokenRepository jetons;
    private final CsrfAuthenticationStrategy renouvellementJeton;
    private final Duration inactivite;
    private final boolean cookieSecurise;

    public SessionUtilisateur(SecurityContextRepository contextes, CsrfTokenRepository jetons,
            CsrfAuthenticationStrategy renouvellementJeton, Duration inactivite, boolean cookieSecurise) {
        this.contextes = contextes;
        this.jetons = jetons;
        this.renouvellementJeton = renouvellementJeton;
        this.inactivite = inactivite;
        this.cookieSecurise = cookieSecurise;
    }

    public void ouvrir(HttpServletRequest requete, HttpServletResponse reponse) {
        HttpSession session = requete.getSession(false);
        if (session == null) {
            session = requete.getSession(true);
        } else {
            requete.changeSessionId();
        }
        session.setMaxInactiveInterval(Math.toIntExact(inactivite.toSeconds()));

        Authentication authentification = UsernamePasswordAuthenticationToken.authenticated(PROPRIETAIRE, null, List.of());
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(authentification);
        SecurityContextHolder.setContext(contexte);
        contextes.saveContext(contexte, requete, reponse);
        renouvellementJeton.onAuthentication(authentification, requete, reponse);
    }

    public void fermer(HttpServletRequest requete, HttpServletResponse reponse) {
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        jetons.saveToken(null, requete, reponse);
        ResponseCookie cookieExpire = ResponseCookie.from(COOKIE_SESSION, "")
                .path("/").maxAge(0).httpOnly(true).secure(cookieSecurise).sameSite("Lax").build();
        reponse.addHeader(HttpHeaders.SET_COOKIE, cookieExpire.toString());
    }
}
