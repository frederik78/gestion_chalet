package ca.gestionchalet.service;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.DriveClient;
import ca.gestionchalet.client.DriveClient.DrivePhoto;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.DepositRetryRules;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le dépôt des photos des reçus validés dans Drive (D-60, D-66) : les appelants signalent des faits (un reçu validé, une autorisation accordée), et ce module décide de l'ordre,
 * des pannes et des essais. Il n'écrit jamais dans le Sheet : celui-ci n'est écrit que par une régénération demandée (R-44).
 *
 * <p>La base sert de file : une photo sans identifiant Drive est « à déposer », une photo déposée ne l'est jamais deux fois (R-29). Un seul fil dépose, réveillé après chaque
 * validation, à chaque autorisation accordée, à l'heure du nouvel essai d'une photo en panne, puis périodiquement ; il reprend ainsi, après un redémarrage, toute photo à
 * déposer (R-20). <b>Aucune transaction ne reste ouverte pendant un appel à Google</b> : une transaction courte lit les photos à déposer, une autre garde l'identifiant Drive reçu.
 *
 * <p>Sans autorisation valide, aucun appel ne part, pas même celui d'un jeton : les photos attendent la nouvelle autorisation, qui les reprend aussitôt. Une panne passagère
 * reporte la photo à l'heure que donne {@link DepositRetryRules} (1, 2, 4 minutes… jusqu'à 1 heure, ou le {@code Retry-After} de Google) ; cette heure est en mémoire seulement, car un
 * redémarrage vaut un nouvel essai de toute photo.
 */
@Service
public class RecuCopyService {

    private static final Logger LOG = LoggerFactory.getLogger(RecuCopyService.class);

    private final RecuRepository recuRepository;
    private final GoogleAuthorizationService googleAuthorizationService;
    private final DriveClient driveClient;
    private final PhotoStorage photoStorage;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<TaskScheduler> scheduler;
    private final Clock clock;
    private final DepositRetryRules retryRules;
    /** Les photos en panne : le nombre d'échecs et l'heure du prochain essai. */
    private final Map<UUID, Retry> retries = new ConcurrentHashMap<>();

    public RecuCopyService(RecuRepository recuRepository, GoogleAuthorizationService googleAuthorizationService, DriveClient driveClient, PhotoStorage photoStorage,
            TransactionTemplate transactionTemplate, ObjectProvider<TaskScheduler> scheduler, Clock clock, DepositRetryRules retryRules) {
        this.recuRepository = recuRepository;
        this.googleAuthorizationService = googleAuthorizationService;
        this.driveClient = driveClient;
        this.photoStorage = photoStorage;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = scheduler;
        this.clock = clock;
        this.retryRules = retryRules;
    }

    /** Une photo à déposer, lue du reçu au premier temps ; le nom Drive vient de la date du reçu. */
    private record Pending(UUID recuId, LocalDate date, Photo photo) {
    }

    private record Retry(int failures, Instant at) {
    }

    /** Un reçu vient d'être validé, dans la transaction de la validation : le dépôt est réveillé une fois la validation enregistrée, sans que la réponse l'attende (CA45). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receiptValidated() {
        wakeAfterCommit(clock.instant());
    }

    /** Une nouvelle autorisation reprend aussitôt les dépôts en attente, y compris ceux dont l'essai était programmé plus tard (CA65). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GoogleAuthorizationGranted event) {
        retries.clear();
        wakeAfterCommit(clock.instant());
    }

    /**
     * Dépose les photos dues, une à une, dans l'ordre de validation des reçus : c'est le passage périodique, et aussi ce que réveillent une validation, une autorisation et l'heure d'un
     * essai. Les dépôts à faire restent à faire quand l'autorisation manque.
     */
    @Scheduled(fixedDelayString = "${recus.copie.intervalle}")
    public synchronized void processPending() {
        if (!googleAuthorizationService.isValid()) {
            return;
        }
        for (Pending pending : pendingPhotos()) {
            Retry retry = retries.get(pending.photo().getId());
            if ((retry == null || !retry.at().isAfter(clock.instant())) && !deposit(pending)) {
                return;
            }
        }
    }

    private List<Pending> pendingPhotos() {
        return transactionTemplate.execute(status -> recuRepository.findWithPhotosToDeposit().stream()
                .sorted(Comparator.comparing(Recu::getValideLe))
                .flatMap(recu -> recu.getPhotos().stream().filter(photo -> photo.getDriveFileId() == null)
                        .map(photo -> new Pending(recu.getId(), recu.getValeurs().date(), photo)))
                .toList());
    }

    /** @return faux si l'autorisation manque : les autres photos attendent aussi, inutile d'en essayer une autre */
    private boolean deposit(Pending pending) {
        Photo photo = pending.photo();
        try {
            String fileId = driveClient.deposit(new DrivePhoto(photo.getId(), driveName(pending), photo.getFormat().mediaType(), photoStorage.read(photo)));
            keep(pending, fileId);
            retries.remove(photo.getId());
        } catch (BlockedGoogleException e) {
            if (e.motif() == MotifBlocage.AUTORISATION_GOOGLE) {
                LOG.warn("Dépôt de la photo {} : autorisation Google manquante", photo.getId());
                return false;
            }
            LOG.warn("Dépôt de la photo {} : refusé par Drive ({})", photo.getId(), e.motif());
            scheduleRetry(photo.getId(), retryRules.maximum());
        } catch (TransientGoogleException e) {
            LOG.warn("Dépôt de la photo {} : panne passagère ({})", photo.getId(), e.getMessage());
            failed(photo.getId(), e.retryAfter());
        } catch (RuntimeException e) {
            // Jamais le message : il peut citer une valeur de reçu.
            LOG.error("Dépôt de la photo {} interrompu : {}", photo.getId(), e.getClass().getName());
            failed(photo.getId(), Optional.empty());
        }
        return true;
    }

    /** Le nom d'un fichier de Drive est généré par le serveur, jamais repris du client (R-36, CS12). */
    private static String driveName(Pending pending) {
        return pending.date() + "_" + pending.photo().getId() + "." + pending.photo().getFormat().extension();
    }

    private void keep(Pending pending, String fileId) {
        transactionTemplate.executeWithoutResult(status -> recuRepository.findById(pending.recuId()).orElseThrow().getPhotos().stream()
                .filter(photo -> photo.getId().equals(pending.photo().getId())).forEach(photo -> photo.deposer(fileId)));
    }

    /** Une panne passagère : le prochain essai suit le rythme de R-20, et le {@code Retry-After} de Google, s'il est plus long, l'emporte. */
    private void failed(UUID photoId, Optional<Duration> requestedByGoogle) {
        scheduleRetry(photoId, retryRules.delayAfter(failures(photoId) + 1, requestedByGoogle));
    }

    /** Un refus de Drive qui attend l'utilisateur est réessayé à l'intervalle maximal (R-20). */
    private void scheduleRetry(UUID photoId, Duration wait) {
        Instant at = clock.instant().plus(wait);
        retries.put(photoId, new Retry(failures(photoId) + 1, at));
        Optional.ofNullable(scheduler.getIfUnique()).ifPresent(taskScheduler -> taskScheduler.schedule(this::processPending, at));
    }

    private int failures(UUID photoId) {
        return Optional.ofNullable(retries.get(photoId)).map(Retry::failures).orElse(0);
    }

    /** Réveille le fil de dépôt une fois la transaction en cours enregistrée : plus tôt, il ne verrait pas encore le reçu. Sans fil planifié (tests), rien ne part. */
    private void wakeAfterCommit(Instant at) {
        TaskScheduler taskScheduler = scheduler.getIfUnique();
        if (taskScheduler == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskScheduler.schedule(RecuCopyService.this::processPending, at);
            }
        });
    }
}
