package ca.gestionchalet.web.rest.errors;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Écrit une erreur générique hors de Spring MVC : filtres de sécurité, pare-feu HTTP, serveur. */
public final class ErrorResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ErrorResponses() {
    }

    public static String json(ErrorCode code) {
        return JSON.writeValueAsString(ErrorDTO.of(code));
    }

    public static void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.resetBuffer();
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(json(code).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
