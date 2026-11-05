package ca.gestionchalet.client;

import ca.gestionchalet.domain.MotifBlocage;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ce que Drive et Sheets ont en commun : le jeton d'accès, demandé à chaque opération, et le classement des pannes (D-60, R-20). Une panne est passagère, ou bloque la copie
 * pour un motif ; rien d'autre n'en sort : ni le corps de la réponse, ni le contenu de la requête, qui peuvent citer un reçu.
 */
final class GoogleCalls {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalls.class);

    private final Supplier<Optional<String>> accessTokens;

    GoogleCalls(Supplier<Optional<String>> accessTokens) {
        this.accessTokens = accessTokens;
    }

    /** Sans jeton, aucune requête ne part : l'autorisation manque, est révoquée ou refusée, et les reçus en attente sont déjà bloqués. */
    String bearer() {
        try {
            return "Bearer " + accessTokens.get().orElseThrow(() -> new BlockedGoogleException(MotifBlocage.AUTORISATION_GOOGLE));
        } catch (OAuth2AuthorizationException e) {
            throw new TransientGoogleException("JETON_INDISPONIBLE", e);
        }
    }

    /** Une réponse 2xx illisible, ou qui ne contient pas ce qu'on attend, est une panne passagère (R-20). */
    <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw classify(e.getStatusCode());
        } catch (RestClientException e) {
            LOG.warn("Google : {}", e.getClass().getSimpleName());
            throw new TransientGoogleException(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 429 et 5xx : quota ou panne de Google (R-20). 403 et 404 : fichier, dossier ou onglet refusé (section 8 du PRD). 401 : le jeton, qui vient d'être renouvelé, est refusé ;
     * la prochaine tentative le renouvellera, et l'état de l'autorisation, lui, ne change que sur la réponse du renouvellement. Tout autre refus reste passager : rien n'est
     * perdu, et la panne est journalisée par son code.
     */
    private static RuntimeException classify(HttpStatusCode status) {
        LOG.warn("Google : réponse {}", status.value());
        if (status.value() == 403 || status.value() == 404) {
            return new BlockedGoogleException(MotifBlocage.FICHIER_INACCESSIBLE);
        }
        return new TransientGoogleException("HTTP_" + status.value());
    }
}
