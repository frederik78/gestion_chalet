package ca.gestionchalet.config;

import ca.gestionchalet.security.LimiteurConnexion;
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
final class GardeConnexionFilter extends OncePerRequestFilter {

    private final RequestMatcher connexion;
    private final LimiteurConnexion limiteur;
    private final long limite;

    GardeConnexionFilter(RequestMatcher connexion, LimiteurConnexion limiteur, long limite) {
        this.connexion = connexion;
        this.limiteur = limiteur;
        this.limite = limite;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !connexion.matches(requete);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws IOException, ServletException {
        long longueur = requete.getContentLengthLong();
        if (requete.getQueryString() != null) {
            ErrorResponses.write(reponse, ErrorCode.REQUETE_INVALIDE);
        } else if (!estFormulaire(requete.getContentType())) {
            ErrorResponses.write(reponse, ErrorCode.FORMAT_NON_ACCEPTE);
        } else if (longueur < 0 || longueur > limite) {
            ErrorResponses.write(reponse, ErrorCode.TAILLE_EXCESSIVE);
        } else {
            Optional<Duration> restant = limiteur.reserver(requete.getRemoteAddr());
            if (restant.isPresent()) {
                reponse.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(restant.get().plusNanos(999_999_999L).toSeconds()));
                ErrorResponses.write(reponse, ErrorCode.TROP_DE_TENTATIVES);
            } else {
                suite.doFilter(requete, reponse);
            }
        }
    }

    private static boolean estFormulaire(String type) {
        try {
            return type != null && MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(MediaType.parseMediaType(type));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
