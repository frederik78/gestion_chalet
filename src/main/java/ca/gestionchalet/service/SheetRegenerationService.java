package ca.gestionchalet.service;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.client.SheetTab;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.CelluleMemorisee;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.MotifEchec;
import ca.gestionchalet.domain.OngletGenere;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Regeneration;
import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.repository.OngletGenereRepository;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.repository.RegenerationRepository;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import ca.gestionchalet.service.mapper.RegenerationMapper;
import java.time.Clock;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La régénération du Sheet (D-66, D-68) : à la demande du bouton, l'onglet généré de chaque fichier d'année configuré reproduit les reçus de la base. Le Sheet est une projection : la
 * base fait référence, et la régénération ne modifie aucun reçu ni ne dépose aucune photo (R-45).
 *
 * <p><b>Aucune transaction ne reste ouverte pendant un appel à Google</b> : la base sert de trace, par de courtes transactions entre les appels, et un seul fil écrit (R-43). Les reçus
 * sont lus une fois, au début (R-45). Les fichiers sont traités l'un après l'autre, par année croissante, et l'échec d'un fichier arrête la régénération sans défaire les onglets déjà
 * remplacés (R-46). Pour chaque fichier (R-40) : l'onglet temporaire est écrit en entier, puis il remplace le contenu de l'onglet généré en une seule demande, puis il est retiré ; en cas
 * de panne, l'onglet généré reste celui d'avant. L'application ne touche jamais un onglet qu'elle n'a pas créé (R-41) : l'onglet généré est reconnu par l'identifiant qu'elle a noté, jamais
 * par son seul nom. Ce qu'elle a écrit est mémorisé à la réussite du remplacement (R-53).
 */
@Service
public class SheetRegenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(SheetRegenerationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Ce que la régénération lit d'un reçu au début : tout ce qu'il lui faut, hors de la transaction. */
    private record Line(int year, Instant validatedAt, SheetRow row) {
    }

    /** Un onglet du nom de l'onglet généré, ou de l'onglet temporaire, existe sans avoir été créé par l'application (R-41). */
    private static final class UnrecognizedTabException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int year;

        private UnrecognizedTabException(int year) {
            super("ONGLET_NON_RECONNU");
            this.year = year;
        }
    }

    /** L'année du fichier en cours de traitement, pour l'attribuer à un échec. */
    private static final class Progress {

        private Integer year;
    }

    private final RecuRepository recuRepository;
    private final RegenerationRepository regenerationRepository;
    private final OngletGenereRepository ongletGenereRepository;
    private final RegenerationMapper mapper;
    private final SheetClient sheetClient;
    private final RegenerationRules rules;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final TaskExecutor worker;

    public SheetRegenerationService(RecuRepository recuRepository, RegenerationRepository regenerationRepository, OngletGenereRepository ongletGenereRepository,
            RegenerationMapper mapper, SheetClient sheetClient, RegenerationRules rules, TransactionTemplate transactionTemplate, Clock clock,
            @Qualifier("regenerationExecutor") TaskExecutor worker) {
        this.recuRepository = recuRepository;
        this.regenerationRepository = regenerationRepository;
        this.ongletGenereRepository = ongletGenereRepository;
        this.mapper = mapper;
        this.sheetClient = sheetClient;
        this.rules = rules;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.worker = worker;
    }

    /**
     * Le bouton : l'état passe à {@code EN_COURS}, puis le fil unique écrit le Sheet. Une tâche que l'exécuteur rejette (arrêté ou plein) ne laisse pas l'état en cours : il passe à
     * {@code ECHEC}, et la demande échoue.
     */
    public void request() {
        transactionTemplate.executeWithoutResult(status -> {
            Regeneration regeneration = regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseGet(Regeneration::jamais);
            regeneration.demander(clock.instant());
            regenerationRepository.save(regeneration);
        });
        try {
            worker.execute(this::run);
        } catch (TaskRejectedException e) {
            LOG.error("Régénération : tâche rejetée ({})", e.getClass().getSimpleName());
            fail(MotifEchec.GOOGLE_INDISPONIBLE, null, Set.of());
            throw e;
        }
    }

    public RegenerationStateDTO state() {
        return transactionTemplate.execute(status -> mapper.toDto(regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseGet(Regeneration::jamais)));
    }

    /**
     * La tâche du fil unique : ce qui lui échappe est journalisé par son nom de classe seulement (jamais le message ni la pile : ils peuvent citer un reçu), et l'état ne reste pas
     * {@code EN_COURS} si la base répond. Si la base ne répond pas non plus, l'état reste tel quel : un arrêt pendant l'écriture relève de la reprise au redémarrage.
     */
    private void run() {
        try {
            write();
        } catch (RuntimeException e) {
            LOG.error("Régénération interrompue : {}", e.getClass().getName());
            try {
                fail(MotifEchec.GOOGLE_INDISPONIBLE, null, Set.of());
            } catch (RuntimeException second) {
                LOG.error("Régénération : état non mis à jour ({})", second.getClass().getName());
            }
        }
    }

    private void write() {
        Set<Integer> yearsWithoutFile = new TreeSet<>();
        Progress progress = new Progress();
        try {
            Map<Integer, List<SheetRow>> rowsByYear = readReceiptsByYear();
            yearsWithoutFile.addAll(rowsByYear.keySet());
            yearsWithoutFile.removeAll(rules.files().keySet());
            int written = 0;
            for (Map.Entry<Integer, String> file : new TreeMap<>(rules.files()).entrySet()) {
                progress.year = file.getKey();
                List<SheetRow> rows = rowsByYear.getOrDefault(file.getKey(), List.of());
                regenerate(file.getKey(), file.getValue(), rows);
                written += rows.size();
            }
            succeed(written, yearsWithoutFile);
        } catch (UnrecognizedTabException e) {
            fail(MotifEchec.ONGLET_NON_RECONNU, e.year, yearsWithoutFile);
        } catch (BlockedGoogleException e) {
            fail(motifOf(e.motif()), e.motif() == MotifBlocage.FICHIER_INACCESSIBLE ? progress.year : null, yearsWithoutFile);
        } catch (TransientGoogleException e) {
            LOG.warn("Régénération : panne de Google ({})", e.getClass().getSimpleName());
            fail(MotifEchec.GOOGLE_INDISPONIBLE, null, yearsWithoutFile);
        } catch (RuntimeException e) {
            // Jamais le message : il peut citer une valeur de reçu.
            LOG.error("Régénération interrompue : {}", e.getClass().getName());
            fail(MotifEchec.GOOGLE_INDISPONIBLE, null, yearsWithoutFile);
        }
    }

    private static MotifEchec motifOf(MotifBlocage motif) {
        return switch (motif) {
            case AUTORISATION_GOOGLE -> MotifEchec.AUTORISATION_GOOGLE;
            case FICHIER_INACCESSIBLE -> MotifEchec.FICHIER_INACCESSIBLE;
            default -> MotifEchec.GOOGLE_INDISPONIBLE;
        };
    }

    private void succeed(int written, Set<Integer> yearsWithoutFile) {
        updateState(regeneration -> regeneration.reussir(clock.instant(), written, yearsWithoutFile));
    }

    private void fail(MotifEchec motif, Integer year, Set<Integer> yearsWithoutFile) {
        updateState(regeneration -> regeneration.echouer(clock.instant(), motif, year, yearsWithoutFile));
    }

    private void updateState(Consumer<Regeneration> transition) {
        transactionTemplate.executeWithoutResult(status -> {
            Regeneration regeneration = regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseThrow();
            transition.accept(regeneration);
            regenerationRepository.save(regeneration);
        });
    }

    /** Les lignes de chaque année, triées par date croissante puis par ordre de validation (R-39, Q-16) ; une seule lecture de la base (R-45). */
    private Map<Integer, List<SheetRow>> readReceiptsByYear() {
        List<Line> lines = transactionTemplate.execute(status -> recuRepository.findAll().stream().map(SheetRegenerationService::line).toList());
        Map<Integer, List<SheetRow>> rowsByYear = new TreeMap<>();
        lines.stream().sorted(Comparator.comparing((Line line) -> line.row().date()).thenComparing(Line::validatedAt))
                .forEach(line -> rowsByYear.computeIfAbsent(line.year(), year -> new ArrayList<>()).add(line.row()));
        return rowsByYear;
    }

    private static Line line(Recu recu) {
        List<PhotoLink> links = recu.getPhotos().stream().filter(photo -> photo.getDriveFileId() != null).map(SheetRegenerationService::link).toList();
        return new Line(recu.getValeurs().date().getYear(), recu.getValideLe(), SheetRows.of(recu.getId(), recu.getValeurs(), recu.getAlertes(), links));
    }

    private static PhotoLink link(Photo photo) {
        return new PhotoLink("Photo " + photo.getRang(), photo.getDriveFileId());
    }

    /** Un fichier (R-40) : tout ou rien pour l'onglet généré. */
    private void regenerate(int year, String fileId, List<SheetRow> rows) {
        List<SheetTab> tabs = new ArrayList<>(sheetClient.tabs(fileId));
        removeLeftoverTemporaryTab(year, fileId, tabs);
        String title = rules.generatedTab();
        Optional<Integer> known = knownGeneratedTab(year, fileId, tabs);
        Set<Integer> usedIds = new TreeSet<>(tabs.stream().map(SheetTab::id).toList());
        int targetId = known.orElseGet(() -> freshId(usedIds));
        usedIds.add(targetId);
        int temporaryId = freshId(usedIds);
        updateTab(year, fileId, tab -> tab.noterOngletTemporaire(temporaryId));
        try {
            sheetClient.addTab(fileId, temporaryId, rules.temporaryTab());
            sheetClient.fill(fileId, temporaryId, rows);
            updateTab(year, fileId, tab -> tab.annoncerRemplacement(targetId));
            sheetClient.replace(fileId, temporaryId, targetId, title, known.isEmpty(), rows.size() + 1);
            updateTab(year, fileId, tab -> tab.memoriser(writtenCells(rows)));
        } finally {
            removeTemporaryTab(year, fileId, temporaryId);
        }
    }

    /** Un onglet temporaire que la régénération précédente n'a pas pu retirer (CA89) : elle l'avait noté, il est retiré avant tout. */
    private void removeLeftoverTemporaryTab(int year, String fileId, List<SheetTab> tabs) {
        Integer leftover = readTab(year, fileId).map(OngletGenere::getOngletTemporaireId).orElse(null);
        if (leftover == null) {
            return;
        }
        if (tabs.removeIf(tab -> tab.id() == leftover)) {
            sheetClient.deleteTab(fileId, leftover);
        }
        updateTab(year, fileId, OngletGenere::oublierOngletTemporaire);
    }

    /**
     * L'onglet généré que l'application a créé, par son identité, même renommé ; vide s'il est supprimé (il sera recréé). Un autre onglet qui porte le nom de l'onglet généré ou celui
     * de l'onglet temporaire n'est pas le sien : il n'est jamais touché (R-41).
     */
    private Optional<Integer> knownGeneratedTab(int year, String fileId, List<SheetTab> tabs) {
        Integer remembered = readTab(year, fileId).map(OngletGenere::getOngletId).orElse(null);
        boolean exists = remembered != null && tabs.stream().anyMatch(tab -> tab.id() == remembered);
        boolean foreignGenerated = tabs.stream().anyMatch(tab -> tab.title().equals(rules.generatedTab()) && (!exists || tab.id() != remembered));
        boolean foreignTemporary = tabs.stream().anyMatch(tab -> tab.title().equals(rules.temporaryTab()));
        if (foreignGenerated || foreignTemporary) {
            throw new UnrecognizedTabException(year);
        }
        return exists ? Optional.of(remembered) : Optional.empty();
    }

    /** Hors de l'issue de la régénération, dans un finally : toute panne du retrait, de Google ou de la base, est journalisée sans masquer l'exception d'origine ; l'onglet reste noté, et le prochain passage le retire. */
    private void removeTemporaryTab(int year, String fileId, int temporaryId) {
        try {
            sheetClient.deleteTab(fileId, temporaryId);
            updateTab(year, fileId, OngletGenere::oublierOngletTemporaire);
        } catch (RuntimeException e) {
            LOG.warn("Régénération : onglet temporaire non retiré ({})", e.getClass().getSimpleName());
        }
    }

    private static List<CelluleMemorisee> writtenCells(List<SheetRow> rows) {
        List<CelluleMemorisee> cells = new ArrayList<>();
        for (SheetRow row : rows) {
            List<String> content = SheetRows.writtenCells(row);
            for (int column = 0; column < content.size(); column++) {
                cells.add(new CelluleMemorisee(UUID.fromString(row.rowId()), column, content.get(column)));
            }
        }
        return cells;
    }

    private Optional<OngletGenere> readTab(int year, String fileId) {
        return transactionTemplate.execute(status -> ongletGenereRepository.findById(year).filter(tab -> tab.estDansLeFichier(fileId)));
    }

    /** Une courte transaction sur ce que la régénération mémorise de l'année : jamais ouverte pendant un appel à Google. */
    private void updateTab(int year, String fileId, Consumer<OngletGenere> change) {
        transactionTemplate.executeWithoutResult(status -> {
            OngletGenere tab = ongletGenereRepository.findById(year).orElseGet(() -> OngletGenere.jamaisEcrit(year, fileId));
            if (!tab.estDansLeFichier(fileId)) {
                tab.changerDeFichier(fileId);
            }
            change.accept(tab);
            ongletGenereRepository.save(tab);
        });
    }

    private static int freshId(Set<Integer> used) {
        int id;
        do {
            id = RANDOM.nextInt(1, Integer.MAX_VALUE);
        } while (used.contains(id));
        return id;
    }
}
