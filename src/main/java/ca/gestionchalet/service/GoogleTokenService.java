package ca.gestionchalet.service;

import ca.gestionchalet.security.GoogleErrorCodes;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
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
     * Un appel fait au plus un renouvellement : c'est à l'appelant d'en tenir le rythme (R-20). Une autorisation suspendue (D-57) est essayée elle aussi :
     * Spring Security appelle Google car son jeton d'accès, à l'origine du refus, a expiré.
     *
     * @return le jeton, ou vide si l'autorisation manque, est révoquée ou refusée : les reçus en attente sont alors déjà bloqués
     * @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException sur une panne passagère de Google au sens de R-20 : l'état et les jetons ne changent pas
     */
    public Optional<String> accessToken() {
        // Le gestionnaire exige un client configuré : sans identifiants, il n'est pas appelé.
        if (!googleAuthorizationService.canRenew()) {
            return Optional.empty();
        }
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal(APPLICATION).build();
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(request);
        } catch (ClientAuthorizationException e) {
            String code = e.getError().getErrorCode();
            if (GoogleErrorCodes.isTransient(code) && !isUnreadableRefusal(e)) {
                throw e;
            }
            if (!GoogleErrorCodes.isRevocation(code)) {
                LOG.error("Renouvellement Google refusé : {}", GoogleErrorCodes.loggable(code));
                googleAuthorizationService.suspend();
            }
            return Optional.empty();
        }
        // Un renouvellement réussi peut avoir perdu des droits : l'état fait foi, pas la seule présence d'un jeton.
        return client == null || !googleAuthorizationService.isValid() ? Optional.empty() : Optional.of(client.getAccessToken().getTokenValue());
    }

    /** D-59 : un 401 ou un 403 dont le corps n'est pas un code d'erreur lisible est un refus, non une panne ; un 200 illisible, lui, reste passager. */
    private static boolean isUnreadableRefusal(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                return response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED) || response.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN);
            }
        }
        return false;
    }
}
