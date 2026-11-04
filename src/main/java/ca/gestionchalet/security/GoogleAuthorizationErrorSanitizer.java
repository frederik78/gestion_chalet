package ca.gestionchalet.security;

import ca.gestionchalet.service.GoogleErrorCodes;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Ne laisse sortir du fournisseur d'échange que le code d'erreur, en liste blanche. {@code OAuth2AuthorizationCodeGrantFilter} recopie description et adresse de
 * l'erreur dans sa redirection, sans point d'extension (sa stratégie de redirection est privée et finale) ; la description contient le message
 * de l'exception, l'adresse du serveur de jetons ou le texte reçu. Le fournisseur est le seul maillon remplaçable, par le post-traitement du configurateur.
 */
public final class GoogleAuthorizationErrorSanitizer implements ObjectPostProcessor<AuthenticationProvider> {

    @Override
    @SuppressWarnings("unchecked")
    public <O extends AuthenticationProvider> O postProcess(O provider) {
        return (O) new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                try {
                    return provider.authenticate(authentication);
                } catch (OAuth2AuthorizationException e) {
                    // Sans cause ni message : rien du texte d'origine ne doit pouvoir atteindre une redirection ou un journal.
                    throw new OAuth2AuthorizationException(new OAuth2Error(GoogleErrorCodes.loggable(e.getError().getErrorCode())));
                }
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return provider.supports(authentication);
            }
        };
    }
}
