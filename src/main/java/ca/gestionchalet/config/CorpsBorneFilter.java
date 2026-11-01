package ca.gestionchalet.config;

import ca.gestionchalet.web.rest.errors.CodeErreur;
import ca.gestionchalet.web.rest.errors.ReponsesErreur;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Borne le corps des requêtes hors multipart, que sa longueur soit déclarée ou non (envoi fragmenté) : il est lu en mémoire
 * jusqu'à la limite, puis rejoué. Le multipart a ses propres limites ({@code spring.servlet.multipart.*}), bien plus hautes :
 * il n'est donc accepté qu'avec une session, et refusé sans être lu sur la seule route ouverte sans session.
 */
final class CorpsBorneFilter extends OncePerRequestFilter {

    private static final String PREFIXE_MULTIPART = "multipart/";

    private final AuthenticationTrustResolver authentifications = new AuthenticationTrustResolverImpl();
    private final int limite;

    CorpsBorneFilter(int limite) {
        this.limite = limite;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws IOException, ServletException {
        if (estMultipart(requete)) {
            if (sessionOuverte()) {
                suite.doFilter(requete, reponse);
            } else {
                ReponsesErreur.ecrire(reponse, CodeErreur.FORMAT_NON_ACCEPTE);
            }
            return;
        }
        if (requete.getContentLengthLong() > limite) {
            ReponsesErreur.ecrire(reponse, CodeErreur.TAILLE_EXCESSIVE);
            return;
        }
        byte[] corps = requete.getInputStream().readNBytes(limite + 1);
        if (corps.length > limite) {
            ReponsesErreur.ecrire(reponse, CodeErreur.TAILLE_EXCESSIVE);
            return;
        }
        suite.doFilter(new CorpsRejoue(requete, corps), reponse);
    }

    private boolean sessionOuverte() {
        return authentifications.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean estMultipart(HttpServletRequest requete) {
        String type = requete.getContentType();
        return type != null && type.regionMatches(true, 0, PREFIXE_MULTIPART, 0, PREFIXE_MULTIPART.length());
    }

    private static final class CorpsRejoue extends HttpServletRequestWrapper {

        private final byte[] corps;

        CorpsRejoue(HttpServletRequest requete, byte[] corps) {
            super(requete);
            this.corps = corps;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream flux = new ByteArrayInputStream(corps);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return flux.read();
                }

                @Override
                public int read(byte[] tampon, int debut, int longueur) {
                    return flux.read(tampon, debut, longueur);
                }

                @Override
                public boolean isFinished() {
                    return flux.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener ecouteur) {
                    throw new UnsupportedOperationException("Lecture asynchrone non prise en charge");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encodage = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encodage == null ? StandardCharsets.UTF_8 : Charset.forName(encodage)));
        }
    }
}
