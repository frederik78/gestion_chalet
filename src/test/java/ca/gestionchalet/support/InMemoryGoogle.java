package ca.gestionchalet.support;

import ca.gestionchalet.client.DriveClient;
import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.SheetTarget;
import java.util.ArrayList;
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
    private final Map<UUID, RuntimeException> failuresByPhoto = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean loseDepositResponse;
    private volatile boolean loseWriteResponse;
    private volatile Runnable beforeDeposit = () -> { };
    private volatile Runnable beforeWrite = () -> { };

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

    /** Le prochain dépôt de cette photo échoue avec cette panne ; les dépôts des autres photos réussissent. */
    public void failDepositOf(UUID photoId, RuntimeException failure) {
        failuresByPhoto.put(photoId, failure);
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
        failuresByPhoto.clear();
        writeFailure = null;
        loseDepositResponse = false;
        loseWriteResponse = false;
        beforeDeposit = () -> { };
        beforeWrite = () -> { };
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
        RuntimeException photoFailure = failuresByPhoto.remove(photo.photoId());
        if (photoFailure != null) {
            throw photoFailure;
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
