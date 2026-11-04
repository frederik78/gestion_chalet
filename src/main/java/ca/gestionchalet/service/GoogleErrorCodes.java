package ca.gestionchalet.service;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/** Ce qu'un journal peut dire d'une erreur OAuth2 : un code connu, jamais une valeur reçue telle quelle. */
public final class GoogleErrorCodes {

    private static final String OTHER = "AUTRE";
    /** Posés par Spring Security, absents de {@link OAuth2ErrorCodes}. */
    private static final String INVALID_TOKEN_RESPONSE = "invalid_token_response";
    private static final String INVALID_STATE_PARAMETER = "invalid_state_parameter";

    private static final Set<String> KNOWN = Set.of(
            OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, OAuth2ErrorCodes.ACCESS_DENIED,
            OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE, OAuth2ErrorCodes.INVALID_SCOPE, OAuth2ErrorCodes.SERVER_ERROR,
            OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE, OAuth2ErrorCodes.INVALID_CLIENT, OAuth2ErrorCodes.INVALID_GRANT,
            OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE, INVALID_TOKEN_RESPONSE, INVALID_STATE_PARAMETER);

    /** Spring Security donne {@link #INVALID_TOKEN_RESPONSE} à tout échec sans code de Google : réseau, délai, 429, 5xx ou réponse illisible (R-20). */
    private static final Set<String> TRANSIENT = Set.of(INVALID_TOKEN_RESPONSE, OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE, OAuth2ErrorCodes.SERVER_ERROR);
    /** Ceux pour lesquels le gestionnaire de Spring Security retire le client autorisé. */
    private static final Set<String> REVOCATION = Set.of(OAuth2ErrorCodes.INVALID_GRANT, OAuth2ErrorCodes.INVALID_TOKEN);

    private GoogleErrorCodes() {
    }

    public static String loggable(String code) {
        return KNOWN.contains(code) ? code : OTHER;
    }

    public static boolean isTransient(String code) {
        return TRANSIENT.contains(code);
    }

    public static boolean isRevocation(String code) {
        return REVOCATION.contains(code);
    }
}
