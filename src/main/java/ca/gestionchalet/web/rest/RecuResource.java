package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.RecuService;
import ca.gestionchalet.service.dto.RecuCheckDTO;
import ca.gestionchalet.service.dto.RecuCheckResultDTO;
import ca.gestionchalet.service.dto.RecuCorrectionDTO;
import ca.gestionchalet.service.dto.RecuDTO;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Validation d'un reçu (201 à la création, 200 si sa clé d'idempotence est déjà connue) et correction d'un reçu validé. */
@RestController
@RequestMapping("/api/recus")
public class RecuResource {

    private final RecuService recuService;

    public RecuResource(RecuService recuService) {
        this.recuService = recuService;
    }

    /** Les alertes avant la validation : n'enregistre rien (R-16). */
    @PostMapping(path = "/controles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RecuCheckResultDTO check(@Valid @RequestBody RecuCheckDTO check) {
        return recuService.check(check);
    }

    /** La correction d'un reçu validé : 200 avec le reçu corrigé, 404 s'il n'existe pas, 409 si la version est périmée. */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RecuDTO correct(@PathVariable UUID id, @Valid @RequestBody RecuCorrectionDTO correction) {
        return recuService.correct(id, correction);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecuDTO> validate(
            @Valid @RequestPart("recu") RecuValidationDTO recu,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        RecuValidationResult result = recuService.validate(recu, photos);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.recu());
    }
}
