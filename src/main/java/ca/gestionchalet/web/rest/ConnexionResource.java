package ca.gestionchalet.web.rest;

import ca.gestionchalet.security.SessionUtilisateur;
import ca.gestionchalet.service.ConnexionService;
import ca.gestionchalet.service.dto.ConnexionDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connexion et déconnexion du compte local. */
@RestController
@RequestMapping("/api")
public class ConnexionResource {

    private final ConnexionService connexions;
    private final SessionUtilisateur sessions;

    public ConnexionResource(ConnexionService connexions, SessionUtilisateur sessions) {
        this.connexions = connexions;
        this.sessions = sessions;
    }

    @PostMapping("/connexion")
    ResponseEntity<Void> connecter(@Valid @RequestBody ConnexionDTO corps, HttpServletRequest requete, HttpServletResponse reponse) {
        connexions.connecter(corps.motDePasse());
        sessions.ouvrir(requete, reponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deconnexion")
    ResponseEntity<Void> deconnecter(HttpServletRequest requete, HttpServletResponse reponse) {
        sessions.fermer(requete, reponse);
        return ResponseEntity.noContent().build();
    }
}
