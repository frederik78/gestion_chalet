package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.GoogleAuthorizationService;
import ca.gestionchalet.service.dto.GoogleAuthorizationStateDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google")
public class GoogleResource {

    private final GoogleAuthorizationService googleAuthorizationService;

    public GoogleResource(GoogleAuthorizationService googleAuthorizationService) {
        this.googleAuthorizationService = googleAuthorizationService;
    }

    @GetMapping("/etat")
    public GoogleAuthorizationStateDTO state() {
        return googleAuthorizationService.state();
    }
}
