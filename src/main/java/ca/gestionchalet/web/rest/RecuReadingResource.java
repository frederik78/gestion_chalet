package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.RecuReadingService;
import ca.gestionchalet.service.dto.RecuReadingDTO;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Lecture des photos d'un reçu : 200 quel qu'en soit le résultat, rien n'est enregistré. */
@RestController
@RequestMapping("/api/lectures")
public class RecuReadingResource {

    private final RecuReadingService recuReadingService;

    public RecuReadingResource(RecuReadingService recuReadingService) {
        this.recuReadingService = recuReadingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecuReadingDTO read(@RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return recuReadingService.read(photos);
    }
}
