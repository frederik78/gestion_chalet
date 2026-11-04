package ca.gestionchalet.service;

import java.util.Optional;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

/**
 * Le jeton d'accès pour appeler Google, renouvelé au besoin par Spring Security. À appeler hors transaction : un renouvellement attend Google,
 * et la base n'a qu'un seul lien.
 */
@Service
public class GoogleTokenService {

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
     * @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException sur une panne passagère de Google : l'autorisation reste valide
     */
    public Optional<String> accessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal(APPLICATION).build();
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(request);
        } catch (ClientAuthorizationException e) {
            if (googleAuthorizationService.isValid()) {
                throw e;
            }
            return Optional.empty();
        }
        // Un renouvellement réussi peut avoir perdu des droits : l'état fait foi, pas la seule présence d'un jeton.
        return client == null || !googleAuthorizationService.isValid() ? Optional.empty() : Optional.of(client.getAccessToken().getTokenValue());
    }
}
