package ca.gestionchalet.service;

import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.domain.Regeneration;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.repository.RegenerationRepository;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import ca.gestionchalet.service.mapper.RegenerationMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** La régénération du Sheet (D-66, D-68). */
@Service
public class SheetRegenerationService {

    /** Ce que la régénération lit d'un reçu au début, une fois (R-45) : tout ce qu'il lui faut, hors de la transaction. */
    private record Line(int year, Instant validatedAt, SheetRow row) {
    }

    private final RecuRepository recuRepository;
    private final RegenerationRepository regenerationRepository;
    private final RegenerationMapper mapper;
    private final SheetClient sheetClient;
    private final RegenerationRules rules;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "regeneration-sheet");
        thread.setDaemon(true);
        return thread;
    });

    public SheetRegenerationService(RecuRepository recuRepository, RegenerationRepository regenerationRepository, RegenerationMapper mapper, SheetClient sheetClient,
            RegenerationRules rules, TransactionTemplate transactionTemplate, Clock clock) {
        this.recuRepository = recuRepository;
        this.regenerationRepository = regenerationRepository;
        this.mapper = mapper;
        this.sheetClient = sheetClient;
        this.rules = rules;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    /** Le bouton : l'état passe à {@code EN_COURS}, puis le fil unique écrit le Sheet. */
    public void request() {
        transactionTemplate.executeWithoutResult(status -> {
            Regeneration regeneration = regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseGet(Regeneration::jamais);
            regeneration.demander(clock.instant());
            regenerationRepository.save(regeneration);
        });
        worker.execute(this::run);
    }

    public RegenerationStateDTO state() {
        return transactionTemplate.execute(status -> mapper.toDto(regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseGet(Regeneration::jamais)));
    }

    private void run() {
        List<Line> lines = readReceipts();
        Map<Integer, List<SheetRow>> rowsByYear = new TreeMap<>();
        lines.stream().sorted(Comparator.comparing(Line::row, Comparator.comparing(SheetRow::date)).thenComparing(Line::validatedAt))
                .forEach(line -> rowsByYear.computeIfAbsent(line.year(), year -> new java.util.ArrayList<>()).add(line.row()));
        int written = 0;
        for (Map.Entry<Integer, String> file : new TreeMap<>(rules.files()).entrySet()) {
            List<SheetRow> rows = rowsByYear.getOrDefault(file.getKey(), List.of());
            int sheetId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            sheetClient.addTab(file.getValue(), sheetId, rules.generatedTab());
            sheetClient.fill(file.getValue(), sheetId, rows);
            written += rows.size();
        }
        int total = written;
        transactionTemplate.executeWithoutResult(status -> {
            Regeneration regeneration = regenerationRepository.findById(Regeneration.UNIQUE_ID).orElseThrow();
            regeneration.reussir(clock.instant(), total, List.of());
            regenerationRepository.save(regeneration);
        });
    }

    private List<Line> readReceipts() {
        return transactionTemplate.execute(status -> recuRepository.findAll().stream().map(SheetRegenerationService::line).toList());
    }

    private static Line line(Recu recu) {
        List<PhotoLink> links = recu.getPhotos().stream().filter(photo -> photo.getDriveFileId() != null).map(SheetRegenerationService::link).toList();
        return new Line(recu.getValeurs().date().getYear(), recu.getValideLe(), SheetRows.of(recu.getId(), recu.getValeurs(), recu.getAlertes(), links));
    }

    private static PhotoLink link(Photo photo) {
        return new PhotoLink("Photo " + photo.getRang(), photo.getDriveFileId());
    }
}
