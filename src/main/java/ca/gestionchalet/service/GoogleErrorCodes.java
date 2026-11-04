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

    private GoogleErrorCodes() {
    }

    public static String loggable(String code) {
        return KNOWN.contains(code) ? code : OTHER;
    }
}
