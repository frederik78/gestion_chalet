package ca.gestionchalet.config;

import ca.gestionchalet.web.rest.errors.ErrorCode;
import ca.gestionchalet.web.rest.errors.ErrorResponses;
import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.MediaType;

/**
 * Remplace la page d'erreur HTML de Tomcat. Une requête que le serveur rejette avant l'application (chemin encodé, en-tête
 * démesuré...) reçoit le corps générique et les en-têtes de sécurité, sans version ni trace.
 */
public class JsonErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable cause) {
        if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        ErrorCode code = ErrorCode.fromStatus(response.getStatus());
        SecurityHeaders.apply(response);
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(ErrorResponses.json(code));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            container.getLogger().warn("Réponse d'erreur non écrite : " + e.getClass().getName());
        }
    }
}
