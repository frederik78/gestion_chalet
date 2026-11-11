package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.SheetRegenerationService;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La régénération de l'onglet généré du Sheet (D-66) : le bouton la demande, l'état se lit. Ni fichier, ni onglet, ni valeur ne viennent de la requête (R-61). */
@RestController
@RequestMapping("/api/sheet")
public class SheetResource {

    private final SheetRegenerationService regenerationService;

    public SheetResource(SheetRegenerationService regenerationService) {
        this.regenerationService = regenerationService;
    }

    /** Demande prise en compte : l'état est {@code EN_COURS}, et l'écriture suit sans que la réponse l'attende. Elle n'importe rien (R-60). */
    @PostMapping("/regeneration")
    public ResponseEntity<Void> regenerate() {
        regenerationService.request();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/etat")
    public RegenerationStateDTO state() {
        return regenerationService.state();
    }
}
