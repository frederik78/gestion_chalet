package ca.gestionchalet.service;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.DriveClient;
import ca.gestionchalet.client.DriveClient.DrivePhoto;
import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.Alerte;
import ca.gestionchalet.domain.EtatCopie;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Le cycle de la copie d'un reçu validé vers Drive et le Sheet (D-60) : les appelants signalent des faits (un reçu validé, une autorisation accordée ou perdue),
 * et ce module décide de l'ordre, des pannes et des essais. Seul ce module change l'état de la copie d'un reçu (règle d'architecture).
 *
 * <p>Une copie se fait en trois temps, et <b>aucune transaction ne reste ouverte pendant un appel à Google</b> : le lien unique vers la base (R-19 : un seul fil copie)
 * serait tenu pendant le réseau. (1) Une transaction courte lit le reçu et en tire ce qu'il faut. (2) Hors transaction, les photos sont déposées une à une dans Drive, puis,
 * toutes déposées (R-18), la ligne est écrite. (3) Une transaction courte garde les fichiers déposés et change l'état. La base sert de file : les reçus {@code A_COPIER}, par
 * ordre de validation (R-19) ; un seul fil les prend, réveillé après chaque validation et périodiquement. Une panne passagère laisse le reçu {@code A_COPIER}, repris au
 * prochain passage ; le rythme des essais (R-20) et la reprise des reçus bloqués viennent avec 001-08.
 */
@Service
public class RecuCopyService {

    private static final Logger LOG = LoggerFactory.getLogger(RecuCopyService.class);
    private static final String NOTES_SEPARATOR = " ; ";

    private final RecuRepository recuRepository;
    private final GoogleAuthorizationService googleAuthorizationService;
    private final DriveClient driveClient;
    private final SheetClient sheetClient;
    private final PhotoStorage photoStorage;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<TaskScheduler> scheduler;
    private final Clock clock;

    public RecuCopyService(RecuRepository recuRepository, GoogleAuthorizationService googleAuthorizationService, DriveClient driveClient, SheetClient sheetClient,
            PhotoStorage photoStorage, TransactionTemplate transactionTemplate, ObjectProvider<TaskScheduler> scheduler, Clock clock) {
        this.recuRepository = recuRepository;
        this.googleAuthorizationService = googleAuthorizationService;
        this.driveClient = driveClient;
        this.sheetClient = sheetClient;
        this.photoStorage = photoStorage;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Ce qu'une copie lit du reçu au premier temps : tout ce qu'il lui faut, hors de la transaction. */
    private record Job(UUID recuId, int version, Valeurs valeurs, List<Alerte> alertes, List<Photo> photos) {
    }

    /** Ce que le deuxième temps rend : les fichiers déposés, même si la suite a échoué, et l'issue. */
    private record Outcome(Map<UUID, String> deposited, Optional<MotifBlocage> blocked, boolean copied) {
    }

    /** Un reçu vient d'être validé, dans la transaction de la validation : sans autorisation Google, il attend (CA64) ; sinon, la copie est réveillée une fois la validation enregistrée. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receiptValidated(Recu recu) {
        if (googleAuthorizationService.isValid()) {
            wakeAfterCommit();
        } else {
            recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE);
        }
    }

    /** Une nouvelle autorisation reprend les reçus bloqués pour ce motif, et la copie est réveillée aussitôt (CA65, section 7.6). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GoogleAuthorizationGranted event) {
        recuRepository.findByEtatCopieAndMotifBlocage(EtatCopie.BLOQUEE, MotifBlocage.AUTORISATION_GOOGLE).forEach(Recu::reprendre);
        wakeAfterCommit();
    }

    /** Une autorisation perdue bloque les reçus en attente (CA66). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GoogleAuthorizationLost event) {
        recuRepository.findByEtatCopie(EtatCopie.A_COPIER).forEach(recu -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE));
    }

    /** Le passage périodique : chaque reçu à copier, une fois, dans l'ordre de validation. Un reçu en échec ne retient pas les suivants. */
    @Scheduled(fixedDelayString = "${recus.copie.intervalle}")
    public synchronized void processPending() {
        for (UUID id : pendingIds()) {
            try {
                copy(id);
            } catch (RuntimeException e) {
                // Jamais le message : il peut citer une valeur de reçu.
                LOG.error("Copie du reçu {} interrompue : {}", id, e.getClass().getName());
            }
        }
    }

    /**
     * Copie le premier reçu à copier, dans l'ordre de validation (R-19).
     *
     * @return faux s'il n'y en avait aucun
     */
    public synchronized boolean processNext() {
        Optional<UUID> next = pendingIds().stream().findFirst();
        next.ifPresent(this::copy);
        return next.isPresent();
    }

    private List<UUID> pendingIds() {
        return transactionTemplate.execute(status -> recuRepository.findByEtatCopie(EtatCopie.A_COPIER).stream()
                .sorted(Comparator.comparing(Recu::getValideLe))
                .map(Recu::getId)
                .toList());
    }

    private void copy(UUID id) {
        Optional<Job> job = transactionTemplate.execute(status -> recuRepository.findById(id)
                .filter(recu -> recu.getEtatCopie() == EtatCopie.A_COPIER)
                .map(recu -> new Job(recu.getId(), recu.getVersion(), recu.getValeurs(), recu.getAlertes(), recu.getPhotos())));
        job.ifPresent(todo -> finish(todo, send(todo)));
    }

    /** Hors transaction : toutes les photos d'abord (R-18), puis la ligne. Une photo déjà déposée n'est pas redéposée. */
    private Outcome send(Job job) {
        Map<UUID, String> deposited = new LinkedHashMap<>();
        try {
            List<PhotoLink> links = new ArrayList<>();
            for (Photo photo : job.photos()) {
                String fileId = photo.getDriveFileId();
                if (fileId == null) {
                    fileId = driveClient.deposit(new DrivePhoto(photo.getId(), driveName(job.valeurs(), photo), photo.getFormat().mediaType(), photoStorage.read(photo)));
                    deposited.put(photo.getId(), fileId);
                }
                links.add(new PhotoLink("Photo " + photo.getRang(), fileId));
            }
            sheetClient.write(row(job, links));
            return new Outcome(deposited, Optional.empty(), true);
        } catch (BlockedGoogleException e) {
            return new Outcome(deposited, Optional.of(e.motif()), false);
        } catch (TransientGoogleException | UncheckedIOException e) {
            LOG.warn("Copie du reçu {} : panne passagère ({})", job.recuId(), e.getClass().getSimpleName());
            return new Outcome(deposited, Optional.empty(), false);
        }
    }

    /** Le nom d'un fichier de Drive est généré par le serveur, jamais repris du client (R-36, CS12). */
    private static String driveName(Valeurs valeurs, Photo photo) {
        return valeurs.date() + "_" + photo.getId() + "." + photo.getFormat().extension();
    }

    /** Les numéros d'inscription n'y figurent pas (R-25) ; NOTES porte les textes des alertes dans l'ordre du tableau du PRD (R-26). */
    private static SheetRow row(Job job, List<PhotoLink> links) {
        Valeurs valeurs = job.valeurs();
        String notes = job.alertes().stream().sorted(Comparator.comparing(alerte -> alerte.code().ordinal())).map(Alerte::message)
                .collect(Collectors.joining(NOTES_SEPARATOR));
        return new SheetRow(job.recuId().toString(), valeurs.date(), valeurs.fournisseur(), valeurs.objet(), valeurs.typeDepense(),
                dollars(valeurs.montantAvantTaxesCents()), dollars(valeurs.tpsCents()), dollars(valeurs.tvqCents()), dollars(valeurs.montantTotalCents()), notes, links);
    }

    /** Des cents entiers aux dollars, sans flottant ; une taxe absente reste absente (R-08). */
    private static BigDecimal dollars(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    /** Troisième temps : les fichiers déposés sont gardés quelle que soit l'issue, et l'état change seulement si le reçu est toujours à copier. */
    private void finish(Job job, Outcome outcome) {
        transactionTemplate.executeWithoutResult(status -> {
            Recu recu = recuRepository.findById(job.recuId()).orElseThrow();
            recu.getPhotos().forEach(photo -> Optional.ofNullable(outcome.deposited().get(photo.getId())).ifPresent(photo::deposer));
            if (recu.getEtatCopie() != EtatCopie.A_COPIER) {
                return;
            }
            if (outcome.copied()) {
                if (!recu.terminerCopie(job.version())) {
                    LOG.info("Copie du reçu {} : corrigé pendant la copie, à refaire", job.recuId());
                }
            } else if (outcome.blocked().isPresent()) {
                recu.bloquer(outcome.blocked().get());
                LOG.warn("Copie du reçu {} : BLOQUEE ({})", job.recuId(), outcome.blocked().get());
            } else {
                recu.reessayer();
            }
        });
    }

    /** Réveille le fil de la copie une fois la transaction en cours enregistrée : plus tôt, il ne verrait pas encore le reçu. Sans fil planifié (tests), rien ne part. */
    private void wakeAfterCommit() {
        TaskScheduler taskScheduler = scheduler.getIfUnique();
        if (taskScheduler == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskScheduler.schedule(RecuCopyService.this::processPending, clock.instant());
            }
        });
    }
}
