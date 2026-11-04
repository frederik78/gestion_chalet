package ca.gestionchalet.service;

import ca.gestionchalet.domain.IdempotencyKeyConflictException;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import ca.gestionchalet.service.mapper.RecuMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Enregistre un reçu et ses photos à la validation. */
@Service
public class RecuService {

    private final RecuRepository recuRepository;
    private final PhotoStorage photoStorage;
    private final RecuMapper recuMapper;
    private final PhotoValidationService photoValidationService;
    private final Clock clock;

    public RecuService(RecuRepository recuRepository, PhotoStorage photoStorage, RecuMapper recuMapper,
            PhotoValidationService photoValidationService, Clock clock) {
        this.recuRepository = recuRepository;
        this.photoStorage = photoStorage;
        this.recuMapper = recuMapper;
        this.photoValidationService = photoValidationService;
        this.clock = clock;
    }

    @Transactional
    public RecuValidationResult validate(RecuValidationDTO validation, List<MultipartFile> files) {
        List<PhotoFormat> formats = photoValidationService.check(files);
        // Le lien unique vers la base (hikari.maximum-pool-size) est pris à l'ouverture de la transaction et tenu jusqu'au commit, hachage et
        // écriture compris (mesuré) : un envoi simultané attend avant d'entrer dans cette méthode, puis voit le reçu du premier.
        Optional<Recu> known = recuRepository.findByCleIdempotence(validation.cleIdempotence());
        Valeurs valeurs = recuMapper.toValeurs(validation.valeurs());
        String fingerprint = ContentFingerprint.of(valeurs, files);
        if (known.isPresent()) {
            if (!known.get().hasContent(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            return new RecuValidationResult(recuMapper.toDto(known.get()), false);
        }
        List<Photo> photos = new ArrayList<>();
        discardFilesOnRollback(photos);
        for (MultipartFile file : files) {
            Photo photo = Photo.of(photos.size() + 1, formats.get(photos.size()), file.getSize());
            photos.add(photo);
            store(photo, file);
        }
        Recu recu = Recu.valider(validation.cleIdempotence(), valeurs, fingerprint,
                validation.montantTotalLuCents(), clock.instant(), photos);
        return new RecuValidationResult(recuMapper.toDto(recuRepository.saveAndFlush(recu)), true);
    }

    /** R-12 : une validation annulée, y compris par un échec au commit, ne laisse aucun fichier. Sur un statut inconnu, rien n'est retiré : un orphelin vaut mieux qu'une pièce perdue. */
    private void discardFilesOnRollback(List<Photo> photos) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    photos.forEach(photoStorage::discard);
                }
            }
        });
    }

    private void store(Photo photo, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            photoStorage.write(photo, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
