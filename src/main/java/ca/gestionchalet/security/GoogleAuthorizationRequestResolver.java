package ca.gestionchalet.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * La demande d'autorisation de {@code GET /api/google/autorisation}, construite par le résolveur de Spring Security, avec PKCE.
 * Deux écarts au résolveur seul : la route du contrat ne porte pas d'identifiant de client, et le filtre de redirection passant
 * avant le contrôle d'accès, une requête sans session ne reçoit pas de demande : elle continue vers le refus par défaut. Sans identifiants
 * configurés non plus : elle continue vers la réponse d'erreur de la ressource.
 */
public final class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String REGISTRATION_ID = "google";
    /** Sans ces deux paramètres, Google ne renvoie un jeton de renouvellement qu'au tout premier consentement. */
    private static final Map<String, Object> OFFLINE_CONSENT = Map.of("access_type", "offline", "prompt", "consent");

    private final RequestMatcher route = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/google/autorisation");
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private final ClientRegistrationRepository registrations;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
        delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/google/autorisation");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
                .andThen(request -> request.additionalParameters(parameters -> parameters.putAll(OFFLINE_CONSENT))));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!route.matches(request) || !trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())
                || registrations.findByRegistrationId(REGISTRATION_ID) == null) {
            return null;
        }
        return delegate.resolve(request, REGISTRATION_ID);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return delegate.resolve(request, clientRegistrationId);
    }
}
