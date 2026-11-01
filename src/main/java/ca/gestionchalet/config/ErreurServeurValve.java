package ca.gestionchalet.config;

import ca.gestionchalet.web.rest.errors.CodeErreur;
import ca.gestionchalet.web.rest.errors.ReponsesErreur;
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
public class ErreurServeurValve extends ErrorReportValve {

    @Override
    protected void report(Request requete, Response reponse, Throwable cause) {
        if (reponse.getStatus() < 400 || reponse.getContentWritten() > 0 || !reponse.setErrorReported()) {
            return;
        }
        CodeErreur code = CodeErreur.pourStatut(reponse.getStatus());
        EnTetesSecurite.appliquer(reponse);
        reponse.setStatus(code.statut());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        try {
            Writer sortie = reponse.getReporter();
            if (sortie != null) {
                sortie.write(ReponsesErreur.json(code));
                reponse.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            container.getLogger().warn("Réponse d'erreur non écrite : " + e.getClass().getName());
        }
    }
}
