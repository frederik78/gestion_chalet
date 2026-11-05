package ca.gestionchalet.service;

import ca.gestionchalet.domain.AlertRules;
import ca.gestionchalet.domain.IdempotencyKeyConflictException;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.RecuCheckDTO;
import ca.gestionchalet.service.dto.RecuCheckResultDTO;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Enregistre un reçu et ses photos à la validation. */
@Service
public class RecuService {

    private final RecuRepository recuRepository;
    private final PhotoStorage photoStorage;
    private final RecuMapper recuMapper;
    private final PhotoValidationService photoValidationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AlertRules alertRules;
    private final RecuCopyService recuCopyService;

    public RecuService(RecuRepository recuRepository, PhotoStorage photoStorage, RecuMapper recuMapper,
            PhotoValidationService photoValidationService, TransactionTemplate transactionTemplate, Clock clock, AlertRules alertRules,
            RecuCopyService recuCopyService) {
        this.recuRepository = recuRepository;
        this.photoStorage = photoStorage;
        this.recuMapper = recuMapper;
        this.photoValidationService = photoValidationService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.alertRules = alertRules;
        this.recuCopyService = recuCopyService;
    }

    /**
     * Le contrôle et l'empreinte passent avant la transaction : le lien unique vers la base (hikari.maximum-pool-size) y est pris à l'ouverture et tenu jusqu'au
     * commit, écriture comprise (mesuré). Une photo refusée ne l'attend donc jamais, et n'atteint ni l'empreinte, ni le disque, ni la recherche de la clé.
     */
    public RecuValidationResult validate(RecuValidationDTO validation, List<MultipartFile> files) {
        List<PhotoFormat> formats = photoValidationService.check(files);
        Valeurs valeurs = recuMapper.toValeurs(validation.valeurs());
        String fingerprint = ContentFingerprint.of(valeurs, files);
        return transactionTemplate.execute(status -> findOrCreate(validation, valeurs, fingerprint, files, formats));
    }

    /** Les alertes de ces valeurs, sans rien enregistrer. */
    public RecuCheckResultDTO check(RecuCheckDTO check) {
        Valeurs valeurs = recuMapper.toValeurs(check.valeurs());
        return new RecuCheckResultDTO(recuMapper.toAlerteDtos(
                alertRules.evaluate(valeurs, check.montantTotalLuCents(), hasPossibleDuplicate(valeurs, check.recuId()))));
    }

    /** Le fournisseur se compare en Java : SQLite ne sait ni retirer les accents ni replier la casse hors ASCII (R-15). */
    private boolean hasPossibleDuplicate(Valeurs valeurs, UUID excludedRecuId) {
        return recuRepository.findByValeursDateAndValeursMontantTotalCents(valeurs.date(), valeurs.montantTotalCents()).stream()
                .anyMatch(other -> !other.getId().equals(excludedRecuId) && other.getValeurs().hasSameSupplier(valeurs));
    }

    /** Un envoi simultané attend le lien avant d'entrer ici, puis voit le reçu du premier. */
    private RecuValidationResult findOrCreate(RecuValidationDTO validation, Valeurs valeurs, String fingerprint, List<MultipartFile> files, List<PhotoFormat> formats) {
        Optional<Recu> known = recuRepository.findByCleIdempotence(validation.cleIdempotence());
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
                validation.montantTotalLuCents(), clock.instant(), photos,
                alertRules.evaluate(valeurs, validation.montantTotalLuCents(), hasPossibleDuplicate(valeurs, null)));
        recuCopyService.receiptValidated(recu);
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
