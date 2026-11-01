package ca.gestionchalet.config;

import ca.gestionchalet.web.rest.errors.CodeErreur;
import ca.gestionchalet.web.rest.errors.ReponsesErreur;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Borne le corps des requêtes hors multipart, que sa longueur soit déclarée ou non (envoi fragmenté) : il est lu en mémoire
 * jusqu'à la limite, puis rejoué. Le multipart a ses propres limites ({@code spring.servlet.multipart.*}).
 */
final class CorpsBorneFilter implements Filter {

    private static final String PREFIXE_MULTIPART = "multipart/";

    private final int limite;

    CorpsBorneFilter(int limite) {
        this.limite = limite;
    }

    @Override
    public void doFilter(ServletRequest requete, ServletResponse reponse, FilterChain suite) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) requete;
        if (estMultipart(http)) {
            suite.doFilter(requete, reponse);
            return;
        }
        if (http.getContentLengthLong() > limite) {
            ReponsesErreur.ecrire((HttpServletResponse) reponse, CodeErreur.TAILLE_EXCESSIVE);
            return;
        }
        byte[] corps = http.getInputStream().readNBytes(limite + 1);
        if (corps.length > limite) {
            ReponsesErreur.ecrire((HttpServletResponse) reponse, CodeErreur.TAILLE_EXCESSIVE);
            return;
        }
        suite.doFilter(new CorpsRejoue(http, corps), reponse);
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
