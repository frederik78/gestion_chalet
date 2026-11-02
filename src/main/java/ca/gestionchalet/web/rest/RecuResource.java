package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.RecuService;
import ca.gestionchalet.service.dto.RecuDTO;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Validation d'un reçu : 201 à la création, 200 si sa clé d'idempotence est déjà connue. */
@RestController
@RequestMapping("/api/recus")
public class RecuResource {

    private final RecuService recuService;

    public RecuResource(RecuService recuService) {
        this.recuService = recuService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecuDTO> validate(
            @RequestPart("recu") RecuValidationDTO recu,
            @RequestPart("photos") List<MultipartFile> photos) {
        RecuValidationResult result = recuService.validate(recu, photos);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.recu());
    }
}
