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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
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
}
