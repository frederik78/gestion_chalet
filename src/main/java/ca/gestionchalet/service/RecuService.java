package ca.gestionchalet.service;

import ca.gestionchalet.domain.MissingPhotoException;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
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
    private final Clock clock;

    public RecuService(RecuRepository recuRepository, PhotoStorage photoStorage, RecuMapper recuMapper, Clock clock) {
        this.recuRepository = recuRepository;
        this.photoStorage = photoStorage;
        this.recuMapper = recuMapper;
        this.clock = clock;
    }

    @Transactional
    public RecuValidationResult validate(RecuValidationDTO validation, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) {
            throw new MissingPhotoException();
        }
        // Le lien unique vers la base (hikari.maximum-pool-size) fait attendre un envoi simultané ici : il voit le reçu du premier.
        Optional<Recu> known = recuRepository.findByCleIdempotence(validation.cleIdempotence());
        if (known.isPresent()) {
            return new RecuValidationResult(recuMapper.toDto(known.get()), false);
        }
        List<Photo> photos = new ArrayList<>();
        discardFilesUnlessCommitted(photos);
        for (MultipartFile file : files) {
            Photo photo = Photo.of(photos.size() + 1, formatOf(file), file.getSize());
            photos.add(photo);
            store(photo, file);
        }
        Recu recu = Recu.valider(validation.cleIdempotence(), recuMapper.toValeurs(validation.valeurs()),
                validation.montantTotalLuCents(), clock.instant(), photos);
        return new RecuValidationResult(recuMapper.toDto(recuRepository.saveAndFlush(recu)), true);
    }

    /** R-12 : quelle que soit la panne, jusqu'à celle du commit, une validation sans reçu ne laisse aucun fichier. */
    private void discardFilesUnlessCommitted(List<Photo> photos) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    photos.forEach(photoStorage::discard);
                }
            }
        });
    }

    private static PhotoFormat formatOf(MultipartFile file) {
        return PhotoFormat.ofMediaType(file.getContentType()).orElseThrow(UnsupportedPhotoFormatException::new);
    }

    private void store(Photo photo, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            photoStorage.write(photo, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
