package ca.gestionchalet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoStorageTest {

    /** Rend 5 Ko, puis la connexion se coupe. */
    private static InputStream fluxCoupe() {
        InputStream coupure = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("flux coupé");
            }
        };
        return new SequenceInputStream(new ByteArrayInputStream(new byte[5120]), coupure);
    }

    @Test
    void ca19_unFluxCoupeNeLaisseNiPhotoTronqueeNiFichierTemporaire(@TempDir Path dossier) throws IOException {
        PhotoStorage stockage = new PhotoStorage(dossier);

        assertThatThrownBy(() -> stockage.write(Photo.of(1, PhotoFormat.PNG, 5120), fluxCoupe()))
                .isInstanceOf(UncheckedIOException.class);

        try (Stream<Path> fichiers = Files.list(dossier)) {
            assertThat(fichiers).isEmpty();
        }
    }

    /** Le système de fichiers peut refuser de forcer un dossier : le code l'accepte, le test n'attend alors que le fichier. */
    private static boolean dossierForcable(Path dossier) {
        try (FileChannel canal = FileChannel.open(dossier, StandardOpenOption.READ)) {
            canal.force(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Observe les vrais {@code force} du JDK par JFR, sans doubler {@code Files} ; le nom temporaire du fichier prouve que son {@code force} précède le déplacement. */
    private static List<String> cheminsForces(Runnable action, Path trace) throws IOException {
        try (Recording enregistrement = new Recording()) {
            enregistrement.enable("jdk.FileForce").withThreshold(Duration.ZERO);
            enregistrement.start();
            action.run();
            enregistrement.stop();
            enregistrement.dump(trace);
        }
        return RecordingFile.readAllEvents(trace).stream()
                .filter(evenement -> evenement.getEventType().getName().equals("jdk.FileForce"))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .map(evenement -> evenement.getString("path"))
                .toList();
    }

    @Test
    void uneEcritureForceLeTemporairePuisLeDossierSurDisque(@TempDir Path racine) throws IOException {
        Path dossier = Files.createDirectory(racine.resolve("photos"));
        boolean forcable = dossierForcable(dossier);
        PhotoStorage stockage = new PhotoStorage(dossier);

        List<String> forces = cheminsForces(
                () -> stockage.write(Photo.of(1, PhotoFormat.PNG, 3), new ByteArrayInputStream(new byte[] {1, 2, 3})), racine.resolve("trace.jfr"));

        assertThat(forces).hasSize(forcable ? 2 : 1);
        assertThat(Path.of(forces.getFirst()).getParent()).isEqualTo(dossier);
        assertThat(forces.getFirst()).endsWith(".tmp");
        if (forcable) {
            assertThat(forces.getLast()).isEqualTo(dossier.toString());
        }
    }
}
