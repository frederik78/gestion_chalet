package ca.gestionchalet.service;

import ca.gestionchalet.client.RecuExtractionException;
import ca.gestionchalet.client.RecuExtractor;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.ReadingResult;
import ca.gestionchalet.domain.RecuReading;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.TooManyReadingsException;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.security.ReadingRateLimiter;
import ca.gestionchalet.service.dto.RecuReadingDTO;
import ca.gestionchalet.service.mapper.RecuReadingMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Fait lire les photos d'un reçu par le modèle, sans rien enregistrer (D-02). */
@Service
public class RecuReadingService {

    private static final Logger LOG = LoggerFactory.getLogger(RecuReadingService.class);
    private static final Valeurs NO_VALUE = new Valeurs(null, null, null, null, null, null, null, null, null, null);

    private final RecuExtractor recuExtractor;
    private final RecuRules rules;
    private final RecuReadingMapper mapper;
    private final ModelFieldsValidationService validation;
    private final PhotoValidationService photoValidationService;
    private final PhotoCopyService photoCopyService;
    private final ReadingRateLimiter rateLimiter;

    public RecuReadingService(RecuExtractor recuExtractor, RecuRules rules, RecuReadingMapper mapper, ModelFieldsValidationService validation,
            PhotoValidationService photoValidationService, PhotoCopyService photoCopyService,
            ReadingRateLimiter rateLimiter) {
        this.recuExtractor = recuExtractor;
        this.rules = rules;
        this.mapper = mapper;
        this.validation = validation;
        this.photoValidationService = photoValidationService;
        this.photoCopyService = photoCopyService;
        this.rateLimiter = rateLimiter;
    }

    public RecuReadingDTO read(List<MultipartFile> files) {
        List<PhotoFormat> formats = photoValidationService.check(files);
        rateLimiter.reserve().ifPresent(wait -> {
            throw new TooManyReadingsException(wait);
        });
        List<byte[]> images = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Optional<byte[]> image = photoCopyService.copy(files.get(i), formats.get(i));
            if (image.isEmpty()) {
                return failure();
            }
            images.add(image.get());
        }
        try {
            return mapper.toDto(validation.validate(recuExtractor.extract(images, rules.typesDepense())));
        } catch (RecuExtractionException e) {
            LOG.warn("Lecture en échec : {}", e.getMessage());
            return failure();
        }
    }

    private RecuReadingDTO failure() {
        return mapper.toDto(new RecuReading(ReadingResult.ECHEC, NO_VALUE, List.of()));
    }
}
