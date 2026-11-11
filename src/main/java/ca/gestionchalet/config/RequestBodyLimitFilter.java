package ca.gestionchalet.config;

import ca.gestionchalet.web.rest.errors.ErrorCode;
import ca.gestionchalet.web.rest.errors.ErrorResponses;
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
 * Borne le corps des requêtes d'une session ouverte, hors multipart, que sa longueur soit déclarée ou non (envoi fragmenté) : il
 * est lu en mémoire jusqu'à la limite, puis rejoué. Sans session, seules les ressources publiques arrivent ici : leur corps
 * n'est pas lu et elles voient un corps vide, car la santé d'Actuator lirait sinon, sans borne, un corps déclaré même en GET. Le multipart a ses propres limites ({@code spring.servlet.multipart.*}), bien plus hautes :
 * sans session, il est refusé, car {@code resolve-lazily} ne suffit pas : sans ce refus, un multipart de 56 Mo reste sans réponse
 * en 5 s (cs08). La connexion a sa propre garde.
 */
final class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final String MULTIPART_PREFIX = "multipart/";

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private final int limit;

    RequestBodyLimitFilter(int limit) {
        this.limit = limit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!isSessionOpen()) {
            if (isMultipart(request)) {
                ErrorResponses.write(response, ErrorCode.FORMAT_NON_ACCEPTE);
            } else {
                chain.doFilter(new ReplayedBodyRequest(request, new byte[0]), response);
            }
            return;
        }
        if (isMultipart(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > limit) {
            ErrorResponses.write(response, ErrorCode.TAILLE_EXCESSIVE);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(limit + 1);
        if (body.length > limit) {
            ErrorResponses.write(response, ErrorCode.TAILLE_EXCESSIVE);
            return;
        }
        chain.doFilter(new ReplayedBodyRequest(request, body), response);
    }

    private boolean isSessionOpen() {
        return trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.regionMatches(true, 0, MULTIPART_PREFIX, 0, MULTIPART_PREFIX.length());
    }

    private static final class ReplayedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        ReplayedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lecture asynchrone non prise en charge");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding)));
        }
    }
}
