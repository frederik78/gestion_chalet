package ca.gestionchalet.service;

import ca.gestionchalet.client.RecuExtractionException;
import ca.gestionchalet.client.RecuExtractor;
import ca.gestionchalet.domain.ReadingResult;
import ca.gestionchalet.domain.RecuReading;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.service.dto.RecuReadingDTO;
import ca.gestionchalet.service.mapper.RecuReadingMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
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

    public RecuReadingService(RecuExtractor recuExtractor, RecuRules rules, RecuReadingMapper mapper, ModelFieldsValidationService validation) {
        this.recuExtractor = recuExtractor;
        this.rules = rules;
        this.mapper = mapper;
        this.validation = validation;
    }

    public RecuReadingDTO read(List<MultipartFile> files) {
        List<byte[]> images = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                images.add(file.getBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return mapper.toDto(validation.validate(recuExtractor.extract(images, rules.typesDepense())));
        } catch (RecuExtractionException e) {
            LOG.warn("Lecture en échec : {}", e.getMessage());
            return mapper.toDto(new RecuReading(ReadingResult.ECHEC, NO_VALUE, List.of()));
        }
    }
}
