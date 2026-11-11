package ca.gestionchalet.support;

import ca.gestionchalet.client.DriveClient;
import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.client.SheetTab;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.SheetTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drive et le Sheet doublés en mémoire : les photos par leur identifiant invisible, les lignes par le leur, comme le fait Google (R-21). Un journal commun garde l'ordre des
 * appels, et les pannes se programment. Aucun appel ne quitte la machine.
 */
public final class InMemoryGoogle implements DriveClient, SheetClient {

    /** Un fichier déposé, tel que Drive le garde. */
    public record DriveFile(String id, String name, String mediaType, byte[] content) {
    }

    private final List<String> journal = new CopyOnWriteArrayList<>();
    private final Map<UUID, DriveFile> files = new LinkedHashMap<>();
    private final Map<String, SheetRow> rows = new LinkedHashMap<>();
    private final Map<String, SheetTarget> targets = new LinkedHashMap<>();
    private final AtomicInteger identifiers = new AtomicInteger();
    private final AtomicInteger depositCalls = new AtomicInteger();
    private final AtomicInteger writeCalls = new AtomicInteger();
    private volatile RuntimeException depositFailure;
    private volatile RuntimeException writeFailure;
    private volatile boolean loseDepositResponse;
    private volatile boolean loseWriteResponse;
    private volatile Runnable beforeDeposit = () -> { };
    private volatile Runnable beforeWrite = () -> { };
    private final Map<String, Map<Integer, MemoryTab>> tabsByFile = new LinkedHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, RuntimeException> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean loseReplaceResponse;
    private volatile Runnable beforeSheetRequest = () -> { };

    /** Les appels dans l'ordre : {@code drive:<photo>} puis {@code sheet:<ligne>}. */
    public List<String> journal() {
        return List.copyOf(journal);
    }

    public synchronized List<DriveFile> files() {
        return List.copyOf(files.values());
    }

    public synchronized DriveFile fileOf(UUID photoId) {
        return files.get(photoId);
    }

    /** Les lignes du Sheet, dans l'ordre où elles ont été créées. */
    public synchronized List<SheetRow> rows() {
        return new ArrayList<>(rows.values());
    }

    /** Où la ligne d'un reçu a été écrite : le fichier et l'onglet, et nulle part ailleurs (D-18). */
    public synchronized SheetTarget targetOf(String rowId) {
        return targets.get(rowId);
    }

    public int depositCalls() {
        return depositCalls.get();
    }

    public int writeCalls() {
        return writeCalls.get();
    }

    /** Le prochain dépôt échoue avec cette panne, sans rien déposer. */
    public void failNextDeposit(RuntimeException failure) {
        depositFailure = failure;
    }

    public void failNextWrite(RuntimeException failure) {
        writeFailure = failure;
    }

    /** Le prochain dépôt crée bien le fichier, mais sa réponse est perdue : l'appelant ne reçoit qu'une panne passagère (CA49). */
    public void loseNextDepositResponse() {
        loseDepositResponse = true;
    }

    /** Idem pour l'écriture de la ligne (CA48). */
    public void loseNextWriteResponse() {
        loseWriteResponse = true;
    }

    /** Exécuté à l'entrée de chaque dépôt : un test peut y bloquer ou y changer le reçu. */
    public void beforeEachDeposit(Runnable hook) {
        beforeDeposit = hook;
    }

    public void beforeEachWrite(Runnable hook) {
        beforeWrite = hook;
    }

    public synchronized void reset() {
        journal.clear();
        files.clear();
        rows.clear();
        targets.clear();
        depositCalls.set(0);
        writeCalls.set(0);
        depositFailure = null;
        writeFailure = null;
        loseDepositResponse = false;
        loseWriteResponse = false;
        beforeDeposit = () -> { };
        beforeWrite = () -> { };
        tabsByFile.clear();
        requests.clear();
        failures.clear();
        loseReplaceResponse = false;
        beforeSheetRequest = () -> { };
    }

    @Override
    public String deposit(DrivePhoto photo) {
        beforeDeposit.run();
        depositCalls.incrementAndGet();
        journal.add("drive:" + photo.photoId());
        RuntimeException failure = depositFailure;
        if (failure != null) {
            depositFailure = null;
            throw failure;
        }
        DriveFile file;
        synchronized (this) {
            file = files.computeIfAbsent(photo.photoId(),
                    id -> new DriveFile("fichier-drive-" + identifiers.incrementAndGet(), photo.name(), photo.mediaType(), photo.content()));
        }
        if (loseDepositResponse) {
            loseDepositResponse = false;
            throw new TransientGoogleException("REPONSE_PERDUE");
        }
        return file.id();
    }

    @Override
    public List<SheetTab> tabs(String fileId) {
        record("tabs", fileId);
        return tabsOf(fileId);
    }

    @Override
    public void addTab(String fileId, int sheetId, String title) {
        record("addTab", fileId, sheetId);
        synchronized (this) {
            Map<Integer, MemoryTab> tabs = file(fileId);
            if (tabs.containsKey(sheetId) || tabs.values().stream().anyMatch(tab -> tab.title.equals(title))) {
                throw new TransientGoogleException("HTTP_400");
            }
            tabs.put(sheetId, new MemoryTab(title));
        }
    }

    /** Un lot : un échec programmé laisse l'onglet à moitié écrit, comme une panne au milieu d'une écriture. */
    @Override
    public void fill(String fileId, int sheetId, List<SheetRow> rows) {
        record("fill", fileId, sheetId);
        RuntimeException failure = takeFailure("fill");
        synchronized (this) {
            MemoryTab tab = tab(fileId, sheetId);
            List<List<Object>> written = new ArrayList<>();
            written.add(new ArrayList<>(List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO", "ID")));
            List<SheetRow> kept = failure == null ? rows : rows.subList(0, rows.size() / 2);
            kept.forEach(row -> written.add(cells(row)));
            tab.cells = written;
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Un lot : tout ou rien, un échec programmé ne change rien. */
    @Override
    public void replace(String fileId, int sourceSheetId, int targetSheetId, String targetTitle, boolean createTarget, int rowCount) {
        record("replace", fileId, sourceSheetId, targetSheetId);
        RuntimeException failure = takeFailure("replace");
        if (failure != null) {
            throw failure;
        }
        synchronized (this) {
            MemoryTab source = tab(fileId, sourceSheetId);
            if (source.cells.size() != rowCount) {
                throw new TransientGoogleException("HTTP_400");
            }
            Map<Integer, MemoryTab> tabs = file(fileId);
            if (createTarget) {
                if (tabs.containsKey(targetSheetId)) {
                    throw new TransientGoogleException("HTTP_400");
                }
                tabs.put(targetSheetId, new MemoryTab(targetTitle));
            }
            MemoryTab target = tab(fileId, targetSheetId);
            target.title = targetTitle;
            target.cells = source.cells.stream().map(row -> (List<Object>) new ArrayList<>(row)).toList();
        }
        if (loseReplaceResponse) {
            loseReplaceResponse = false;
            throw new TransientGoogleException("REPONSE_PERDUE");
        }
    }

    @Override
    public void deleteTab(String fileId, int sheetId) {
        record("deleteTab", fileId, sheetId);
        RuntimeException failure = takeFailure("deleteTab");
        if (failure != null) {
            throw failure;
        }
        synchronized (this) {
            tab(fileId, sheetId);
            file(fileId).remove(sheetId);
        }
    }

    private void record(String operation, String fileId, Integer... sheetIds) {
        requests.add(new Request(operation, fileId, List.of(sheetIds)));
        beforeSheetRequest.run();
        RuntimeException failure = takeFailure(operation + ":any");
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException takeFailure(String operation) {
        return failures.remove(operation);
    }

    /** Ce que Google fait d'une demande vers un fichier inconnu : un refus, que l'application ne contourne pas (elle ne crée jamais le fichier, R-41). */
    private Map<Integer, MemoryTab> file(String fileId) {
        Map<Integer, MemoryTab> tabs = tabsByFile.get(fileId);
        if (tabs == null) {
            throw new BlockedGoogleException(MotifBlocage.FICHIER_INACCESSIBLE);
        }
        return tabs;
    }

    private MemoryTab tab(String fileId, int sheetId) {
        MemoryTab tab = file(fileId).get(sheetId);
        if (tab == null) {
            throw new TransientGoogleException("HTTP_400");
        }
        return tab;
    }

    /** Les cellules telles que Google les garde : typées (date, nombre, texte) ; une valeur écrite par l'application n'est jamais une {@link Formula}. */
    private static List<Object> cells(SheetRow row) {
        return new ArrayList<>(Arrays.asList(row.date(), row.supplier(), emptyToNull(row.object()), row.expenseType(), row.amountBeforeTax(), row.gst(), row.qst(), row.total(),
                emptyToNull(row.notes()), row.photos().isEmpty() ? null : row.photos(), row.rowId()));
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    /** Un fichier du Sheet : sans lui, toute demande est refusée. */
    public synchronized void seedFile(String fileId) {
        tabsByFile.computeIfAbsent(fileId, id -> new LinkedHashMap<>());
    }

    /** Un onglet de l'utilisateur, avec ses cellules (dates, nombres, textes, {@link Formula}). */
    public synchronized void seedTab(String fileId, int sheetId, String title, List<List<Object>> cells) {
        seedFile(fileId);
        MemoryTab tab = new MemoryTab(title);
        tab.cells = cells.stream().map(row -> (List<Object>) new ArrayList<>(row)).toList();
        tabsByFile.get(fileId).put(sheetId, tab);
    }

    public synchronized List<SheetTab> tabsOf(String fileId) {
        return file(fileId).entrySet().stream().map(entry -> new SheetTab(entry.getKey(), entry.getValue().title)).toList();
    }

    /** Les cellules de l'onglet, copiées : la ligne 0 est celle des en-têtes. */
    public synchronized List<List<Object>> cellsOf(String fileId, int sheetId) {
        return tab(fileId, sheetId).cells.stream().map(row -> (List<Object>) new ArrayList<>(row)).toList();
    }

    /** Une modification à la main de l'utilisateur dans l'onglet, ou son renommage, ou sa suppression (CA96). */
    public synchronized void editCell(String fileId, int sheetId, int row, int column, Object value) {
        MemoryTab tab = tab(fileId, sheetId);
        List<List<Object>> edited = new ArrayList<>(tab.cells);
        while (edited.size() <= row) {
            edited.add(new ArrayList<>());
        }
        List<Object> line = new ArrayList<>(edited.get(row));
        while (line.size() <= column) {
            line.add(null);
        }
        line.set(column, value);
        edited.set(row, line);
        tab.cells = edited;
    }

    public synchronized void renameTab(String fileId, int sheetId, String title) {
        tab(fileId, sheetId).title = title;
    }

    public synchronized void removeTab(String fileId, int sheetId) {
        tab(fileId, sheetId);
        file(fileId).remove(sheetId);
    }

    /** Les demandes reçues par le Sheet, lectures comprises, dans l'ordre. */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** La prochaine demande de cette opération ({@code fill}, {@code replace}, {@code deleteTab}) échoue avec cette panne ; {@code tabs:any}, {@code addTab:any}… échouent avant tout effet. */
    public void failNext(String operation, RuntimeException failure) {
        failures.put(operation, failure);
    }

    /** Le prochain remplacement s'applique, mais sa réponse est perdue : l'appelant ne reçoit qu'une panne passagère. */
    public void loseNextReplaceResponse() {
        loseReplaceResponse = true;
    }

    /** Exécuté à l'entrée de chaque demande au Sheet d'onglet : un test peut y observer l'état du Sheet ou de la base au milieu d'une régénération. */
    public void beforeEachSheetRequest(Runnable hook) {
        beforeSheetRequest = hook;
    }

    private static final class MemoryTab {

        private String title;
        private List<List<Object>> cells = List.of();

        private MemoryTab(String title) {
            this.title = title;
        }
    }

    /** Une cellule qui est une formule : seul l'utilisateur en écrit, jamais l'application (CS16). */
    public record Formula(String text) {
    }

    /** Une demande reçue : l'opération, le fichier et les onglets visés. */
    public record Request(String operation, String fileId, List<Integer> sheetIds) {
    }

    @Override
    public void write(SheetTarget target, SheetRow row) {
        beforeWrite.run();
        writeCalls.incrementAndGet();
        journal.add("sheet:" + row.rowId());
        RuntimeException failure = writeFailure;
        if (failure != null) {
            writeFailure = null;
            throw failure;
        }
        synchronized (this) {
            rows.put(row.rowId(), row);
            targets.put(row.rowId(), target);
        }
        if (loseWriteResponse) {
            loseWriteResponse = false;
            throw new TransientGoogleException("REPONSE_PERDUE");
        }
    }
}
