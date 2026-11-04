package ca.gestionchalet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Le client OAuth2 de Spring Security pour Google : mêmes composants que par défaut, avec le délai de {@code recus.google.delai-appel}, sans lequel un Google muet retient l'appel sans fin. */
@Configuration
public class GoogleAuthorizationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAuthorizationConfiguration.class);
    private static final String REGISTRATION_ID = "google";

    private final ApplicationProperties.Google google;
    private final RestClient tokenRestClient;

    public GoogleAuthorizationConfiguration(ApplicationProperties applicationProperties) {
        google = applicationProperties.google();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(applicationProperties.google().delaiAppel());
        requestFactory.setReadTimeout(applicationProperties.google().delaiAppel());
        // Les convertisseurs et le gestionnaire d'erreur que ces clients exigent d'un RestClient fourni (AbstractRestClientOAuth2AccessTokenResponseClient).
        tokenRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
    }

    /**
     * Écrit ici et non par {@code spring.security.oauth2.client} : Spring Boot refuse de démarrer sur un identifiant vide, et D-42 demande de démarrer.
     * Sans identifiants, aucun client n'est connu : aucune demande ne part, et aucun rappel n'aboutit, faute de demande en session.
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(@Value("${recus.google.client-id:}") String clientId,
            @Value("${recus.google.client-secret:}") String clientSecret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            LOG.warn("Autorisation Google non configurée");
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope(google.droits())
                .authorizationUri(google.adresseConsentement())
                .tokenUri(google.adresseJeton())
                .redirectUri(google.adresseRappel())
                .build());
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenResponseClient() {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(tokenRestClient);
        return client;
    }

    /** Sans cadre de requête, pour les copies en tâche de fond. Son gestionnaire d'échec par défaut retire le client autorisé sur {@code invalid_grant}. */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        RestClientRefreshTokenTokenResponseClient refreshClient = new RestClientRefreshTokenTokenResponseClient();
        refreshClient.setRestClient(tokenRestClient);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .refreshToken(refresh -> refresh.accessTokenResponseClient(refreshClient))
                .build());
        return manager;
    }
}
