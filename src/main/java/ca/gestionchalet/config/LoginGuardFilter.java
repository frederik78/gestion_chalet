package ca.gestionchalet.config;

import ca.gestionchalet.security.LoginRateLimiter;
import ca.gestionchalet.web.rest.errors.ErrorCode;
import ca.gestionchalet.web.rest.errors.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Juste avant {@code formLogin}, dont le {@code getParameter} ferait lire le corps par Tomcat et accepterait un mot de passe
 * dans l'URL : seul un formulaire de longueur déclarée et bornée, sans paramètre d'URL, passe. Puis la tentative est comptée
 * en une seule opération : une source bloquée n'atteint jamais bcrypt, et des requêtes simultanées ne dépassent pas le seuil.
 */
final class LoginGuardFilter extends OncePerRequestFilter {

    private final RequestMatcher loginMatcher;
    private final LoginRateLimiter rateLimiter;
    private final long limit;

    LoginGuardFilter(RequestMatcher loginMatcher, LoginRateLimiter rateLimiter, long limit) {
        this.loginMatcher = loginMatcher;
        this.rateLimiter = rateLimiter;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !loginMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long length = request.getContentLengthLong();
        if (request.getQueryString() != null) {
            ErrorResponses.write(response, ErrorCode.REQUETE_INVALIDE);
        } else if (!isForm(request.getContentType())) {
            ErrorResponses.write(response, ErrorCode.FORMAT_NON_ACCEPTE);
        } else if (length < 0 || length > limit) {
            ErrorResponses.write(response, ErrorCode.TAILLE_EXCESSIVE);
        } else {
            Optional<Duration> remaining = rateLimiter.reserve(request.getRemoteAddr());
            if (remaining.isPresent()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(remaining.get().plusNanos(999_999_999L).toSeconds()));
                ErrorResponses.write(response, ErrorCode.TROP_DE_TENTATIVES);
            } else {
                chain.doFilter(request, response);
            }
        }
    }

    private static boolean isForm(String type) {
        try {
            return type != null && MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(MediaType.parseMediaType(type));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
