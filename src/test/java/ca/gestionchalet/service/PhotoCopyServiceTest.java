package ca.gestionchalet.service;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.ReadingRules;
import ca.gestionchalet.support.RecuRequests;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** Les garde-fous du décodage, par leur effet et à leur valeur. Ils relèvent de la section 7.3 du PRD et de la revue de 001-03, sans critère propre. */
class PhotoCopyServiceTest {

    private static final Duration LONG = Duration.ofSeconds(30);

    private static PhotoCopyService service(int coteSource, int simultanes, int passes, Duration delai, Duration attente) {
        return new PhotoCopyService(new ReadingRules(1568, coteSource, simultanes, passes, delai, attente));
    }

    private static Optional<byte[]> copier(PhotoCopyService service, byte[] jpeg) {
        return service.copy(new MockMultipartFile("photos", jpeg), PhotoFormat.JPEG);
    }

    @Test
    void unJpegProgressifOrdinairePasseALaLimiteDePassesEtSEchoueUnCranEnDessous() {
        byte[] jpeg = RecuRequests.jpegProgressif(64);
        int balayages = RecuRequests.balayages(jpeg);
        assertThat(balayages).isEqualTo(10);

        assertThat(copier(service(20000, 1, balayages, LONG, LONG), jpeg)).isPresent();
        assertThat(copier(service(20000, 1, balayages - 1, LONG, LONG), jpeg)).isEmpty();
    }

    @Test
    void desBalayagesVidesSansFinSontInterrompusEtLaPlaceDeDecodageEstRendue() {
        byte[] hostile = RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(512), 100_000);
        PhotoCopyService service = service(20000, 1, 32, LONG, Duration.ofMillis(100));

        Optional<byte[]> copie = assertTimeoutPreemptively(ofSeconds(10), () -> copier(service, hostile));

        assertThat(copie).isEmpty();
        assertThat(copier(service, RecuRequests.jpeg(8))).isPresent();
    }

    /** Mesuré sur le fichier de la revue : à cette échelle le décodeur natif ne rend plus la main, un arrêt pendant le décodage ne suffit pas. */
    @Test
    void uneGrandeImageAuxBalayagesInnombrablesEstRefuseeSansLaDecoder() {
        byte[] hostile = RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(4000), 100_000);
        PhotoCopyService service = service(20000, 1, 32, LONG, LONG);

        Optional<byte[]> copie = assertTimeoutPreemptively(ofSeconds(5), () -> copier(service, hostile));

        assertThat(copie).isEmpty();
    }

    @Test
    void unDecodageAuDelaDuDelaiEstInterrompu() {
        byte[] hostile = RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(512), 100_000);
        PhotoCopyService service = service(20000, 1, Integer.MAX_VALUE, Duration.ofMillis(200), LONG);

        Optional<byte[]> copie = assertTimeoutPreemptively(ofSeconds(10), () -> copier(service, hostile));

        assertThat(copie).isEmpty();
        assertThat(copier(service, RecuRequests.jpeg(8))).isPresent();
    }

    /** Une photo dont l'ouverture attend le test : elle tient sa place de décodage aussi longtemps qu'il le veut. */
    private static final class HeldPhoto extends MockMultipartFile {

        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        HeldPhoto() {
            super("photos", RecuRequests.jpeg(8));
        }

        @Override
        public InputStream getInputStream() throws IOException {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ByteArrayInputStream(getBytes());
        }
    }

    @Test
    void uneSecondePhotoAbandonneSonAttenteQuandLaSeulePlaceEstPrise() throws Exception {
        PhotoCopyService service = service(20000, 1, 32, LONG, Duration.ofMillis(100));
        HeldPhoto tenue = new HeldPhoto();
        CompletableFuture<Optional<byte[]>> premiere = CompletableFuture.supplyAsync(() -> service.copy(tenue, PhotoFormat.JPEG));
        tenue.entered.await();

        Optional<byte[]> seconde = assertTimeoutPreemptively(ofSeconds(10), () -> copier(service, RecuRequests.jpeg(8)));

        assertThat(seconde).isEmpty();
        tenue.release.countDown();
        assertThat(premiere.get()).isPresent();
        assertThat(copier(service, RecuRequests.jpeg(8))).isPresent();
    }

    @Test
    void uneSecondePlaceLaisseUneSecondePhotoSeDecoder() throws Exception {
        PhotoCopyService service = service(20000, 2, 32, LONG, Duration.ofMillis(100));
        HeldPhoto tenue = new HeldPhoto();
        CompletableFuture<Optional<byte[]>> premiere = CompletableFuture.supplyAsync(() -> service.copy(tenue, PhotoFormat.JPEG));
        tenue.entered.await();

        assertThat(copier(service, RecuRequests.jpeg(8))).isPresent();

        tenue.release.countDown();
        assertThat(premiere.get()).isPresent();
    }

    @Test
    void leCoteSourceEstAccepteALaLimiteEtRefuseUnPixelAuDela() {
        assertThat(copier(service(100, 1, 32, LONG, LONG), RecuRequests.jpeg(100))).isPresent();
        assertThat(copier(service(100, 1, 32, LONG, LONG), RecuRequests.jpeg(101))).isEmpty();
    }

    /** Sans dossier de cache utilisable, un encodage qui écrirait la copie sur disque échouerait : elle reste donc en mémoire. */
    @Test
    void laCopieNeTransitePasParLeDisque(@TempDir Path dossier) throws IOException {
        byte[] jpeg = RecuRequests.jpeg(8);
        Path cache = Files.createDirectory(dossier.resolve("cache"));
        ImageIO.setCacheDirectory(cache.toFile());
        Files.delete(cache);
        try {
            assertThat(copier(service(20000, 1, 32, LONG, LONG), jpeg)).isPresent();
        } finally {
            ImageIO.setCacheDirectory(null);
        }
    }
}
