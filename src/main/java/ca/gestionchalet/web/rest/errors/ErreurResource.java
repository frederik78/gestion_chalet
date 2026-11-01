package ca.gestionchalet.web.rest.errors;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Remplace la page d'erreur de Spring Boot : tout renvoi d'erreur du serveur rend le corps générique, sans chemin ni horodatage. */
@RestController
public class ErreurResource implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErreurDTO> erreur(HttpServletRequest requete) {
        Object statut = requete.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        CodeErreur code = statut instanceof Integer valeur ? CodeErreur.pourStatut(valeur) : CodeErreur.INTROUVABLE;
        return ResponseEntity.status(HttpStatus.valueOf(code.statut())).contentType(MediaType.APPLICATION_JSON).body(ErreurDTO.de(code));
    }
}
