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
public final class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

    /** Seule route ouverte sans session : son refus CSRF reste un refus CSRF. Partout ailleurs, l'absence de session prime. */
    private final RequestMatcher loginMatcher;

    public SecurityErrorHandler(RequestMatcher loginMatcher) {
        this.loginMatcher = loginMatcher;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException cause) throws IOException {
        ErrorResponses.write(response, ErrorCode.NON_AUTHENTIFIE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException cause) throws IOException {
        boolean noSession = SecurityContextHolder.getContext().getAuthentication() == null;
        ErrorResponses.write(response, noSession && !loginMatcher.matches(request) ? ErrorCode.NON_AUTHENTIFIE : ErrorCode.CSRF_INVALIDE);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException cause) throws IOException {
        ErrorResponses.write(response, ErrorCode.IDENTIFIANTS_INVALIDES);
    }
}
