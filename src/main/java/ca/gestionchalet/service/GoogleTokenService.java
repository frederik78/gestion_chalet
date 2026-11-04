package ca.gestionchalet.service;

import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Le jeton d'accès pour appeler Google, renouvelé au besoin par Spring Security. À appeler hors transaction : un renouvellement attend Google,
 * et la base n'a qu'un seul lien.
 */
@Service
public class GoogleTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleTokenService.class);
    private static final String REGISTRATION_ID = "google";
    private static final String APPLICATION = "application";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final GoogleAuthorizationService googleAuthorizationService;

    public GoogleTokenService(OAuth2AuthorizedClientManager authorizedClientManager, GoogleAuthorizationService googleAuthorizationService) {
        this.authorizedClientManager = authorizedClientManager;
        this.googleAuthorizationService = googleAuthorizationService;
    }

    /**
     * @return le jeton, ou vide si l'autorisation manque ou vient d'être perdue : les reçus en attente sont alors déjà bloqués
     * @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException sur une panne passagère de Google au sens de R-20 (réseau, délai,
     *         429 ou 5xx) : l'autorisation reste valide. Tout autre échec du renouvellement la fait passer à {@code A_RENOUVELER}.
     */
    public Optional<String> accessToken() {
        // Le gestionnaire exige un client configuré : sans identifiants, il n'est pas appelé.
        if (!googleAuthorizationService.isValid()) {
            return Optional.empty();
        }
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal(APPLICATION).build();
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(request);
        } catch (ClientAuthorizationException e) {
            if (googleAuthorizationService.isValid() && isTransient(e)) {
                throw e;
            }
            if (googleAuthorizationService.isValid()) {
                LOG.error("Renouvellement Google refusé : {}", GoogleErrorCodes.loggable(e.getError().getErrorCode()));
                googleAuthorizationService.removeAuthorizedClient(REGISTRATION_ID, APPLICATION);
            }
            return Optional.empty();
        }
        // Un renouvellement réussi peut avoir perdu des droits : l'état fait foi, pas la seule présence d'un jeton.
        return client == null || !googleAuthorizationService.isValid() ? Optional.empty() : Optional.of(client.getAccessToken().getTokenValue());
    }

    /**
     * Spring Security ne distingue pas ces cas par son code d'erreur : seule la cause garde le réseau ou le statut HTTP. Un délai dépassé en lisant
     * la réponse n'est pas une {@link ResourceAccessException}, mais une {@link IOException} sous une exception de lecture (mesuré : ca66_unGoogleMuet).
     */
    private static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException || cause instanceof IOException) {
                return true;
            }
            if (cause instanceof RestClientResponseException response) {
                return response.getStatusCode().is5xxServerError() || response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
            }
        }
        return false;
    }
}
