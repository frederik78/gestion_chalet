package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.GoogleAuthorizationService;
import ca.gestionchalet.service.dto.GoogleAuthorizationStateDTO;
import ca.gestionchalet.web.rest.errors.ErrorCode;
import ca.gestionchalet.web.rest.errors.ErrorDTO;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google")
public class GoogleResource {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleResource.class);
    private static final URI PWA = URI.create("/");

    private final GoogleAuthorizationService googleAuthorizationService;

    public GoogleResource(GoogleAuthorizationService googleAuthorizationService) {
        this.googleAuthorizationService = googleAuthorizationService;
    }

    /** Atteinte seulement sans identifiants configurés : sinon, le filtre de Spring Security a déjà redirigé vers le consentement. */
    @GetMapping("/autorisation")
    public ResponseEntity<ErrorDTO> authorization() {
        ErrorCode code = ErrorCode.GOOGLE_NON_CONFIGURE;
        return ResponseEntity.status(code.status()).body(ErrorDTO.of(code));
    }

    /**
     * Le rappel valide est traité par le filtre de Spring Security, qui renvoie ici sans paramètre. Tout autre rappel ({@code state} absent,
     * faux, d'une autre session ou déjà utilisé) arrive ici tel quel, sans qu'aucun jeton ait été demandé. L'adresse de sortie est fixe.
     */
    @GetMapping("/rappel")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state) {
        if (code != null || state != null) {
            LOG.warn("Rappel Google refusé : STATE_INVALIDE");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(PWA).build();
    }

    @GetMapping("/etat")
    public GoogleAuthorizationStateDTO state() {
        return googleAuthorizationService.state();
    }
}
