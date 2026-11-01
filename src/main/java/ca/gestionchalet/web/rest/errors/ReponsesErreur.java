package ca.gestionchalet.web.rest.errors;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Écrit une erreur générique hors de Spring MVC : filtres de sécurité, pare-feu HTTP, serveur. */
public final class ReponsesErreur {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ReponsesErreur() {
    }

    public static void ecrire(HttpServletResponse reponse, CodeErreur code) throws IOException {
        reponse.resetBuffer();
        reponse.setStatus(code.statut());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        reponse.getWriter().write(JSON.writeValueAsString(ErreurDTO.de(code)));
        reponse.flushBuffer();
    }
}
