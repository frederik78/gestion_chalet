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

    private static byte[] avecOctetsAvantLaFin(byte[] jpeg, byte[] insere) {
        byte[] sortie = new byte[jpeg.length + insere.length];
        System.arraycopy(jpeg, 0, sortie, 0, jpeg.length - 2);
        System.arraycopy(insere, 0, sortie, jpeg.length - 2, insere.length);
        sortie[sortie.length - 2] = (byte) 0xFF;
        sortie[sortie.length - 1] = (byte) 0xD9;
        return sortie;
    }

    private static byte[] repeter(byte[] motif, int fois) {
        byte[] sortie = new byte[motif.length * fois];
        for (int i = 0; i < fois; i++) {
            System.arraycopy(motif, 0, sortie, i * motif.length, motif.length);
        }
        return sortie;
    }

    private static final byte[] BALAYAGE_VIDE = {(byte) 0xFF, (byte) 0xDA, 0, 8, 1, 1, 0, 1, 63, 0x10};

    /** Un marqueur autonome FF 01 que le décodeur ne lit pas comme un segment : un suivi de la grammaire y voit une longueur et saute les balayages qui suivent. */
    @Test
    void desBalayagesCachesApresUnMarqueurAutonomeSontComptes() {
        // FF 01, puis ce qu'un suivi de la grammaire lirait comme une longueur de 65 535 : les balayages qui suivent lui échappent.
        byte[] cache = {(byte) 0xFF, 0x01, (byte) 0xFF, (byte) 0xFF};
        byte[] hostile = avecOctetsAvantLaFin(RecuRequests.jpegProgressif(64), concat(cache, repeter(BALAYAGE_VIDE, 100)));

        assertThat(copier(service(20000, 1, 32, LONG, LONG), hostile)).isEmpty();
    }

    @Test
    void desBalayagesApresUnSegmentDeLongueurZeroOuUneSontComptes() {
        for (byte[] segment : new byte[][] {{(byte) 0xFF, (byte) 0xE9, 0, 0}, {(byte) 0xFF, (byte) 0xE9, 0, 1}}) {
            byte[] hostile = avecOctetsAvantLaFin(RecuRequests.jpegProgressif(64), concat(segment, repeter(BALAYAGE_VIDE, 100)));

            assertThat(copier(service(20000, 1, 32, LONG, LONG), hostile)).as("longueur " + segment[3]).isEmpty();
        }
    }

    @Test
    void desBalayagesPrecedesDeOctetsFFEtUnFluxCoupeSontComptes() {
        byte[] rempli = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xDA, 0, 8, 1, 1, 0, 1, 63, 0x10};
        byte[] hostile = avecOctetsAvantLaFin(RecuRequests.jpegProgressif(64), repeter(rempli, 100));
        byte[] coupe = java.util.Arrays.copyOf(hostile, hostile.length - 500);

        assertThat(copier(service(20000, 1, 32, LONG, LONG), hostile)).isEmpty();
        assertThat(copier(service(20000, 1, 32, LONG, LONG), coupe)).isEmpty();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] sortie = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, sortie, a.length, b.length);
        return sortie;
    }

    @Test
    void unJpegAvecMiniatureExifEtMpfProgressifsPasseSousLaLimite() {
        byte[] miniature = RecuRequests.jpegProgressif(16);
        byte[] exif = concat("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), miniature);
        byte[] photo = RecuRequests.avecSegment(RecuRequests.jpegProgressif(64), 0xE1, exif);
        photo = RecuRequests.avecSegment(photo, 0xE2, concat("MPF\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), miniature));

        assertThat(RecuRequests.balayages(photo)).isEqualTo(30);
        assertThat(copier(service(20000, 1, 32, LONG, LONG), photo)).isPresent();
    }

    private static byte[] avecSuite(byte[] jpeg, byte[] suite) {
        return concat(jpeg, suite);
    }

    /** Une photo Motion : des FF DA et un FF D9 dans une miniature (Exif), un seul vrai balayage, puis une carte de gain et une vidéo aux FF DA nombreux après la fin d'image. */
    @Test
    void uneMotionPhotoAuxFFDANombreuxHorsDeLImageEstCopiee() {
        byte[] exif = concat(concat(RecuRequests.exif(1, true, "t"), repeter(new byte[] {(byte) 0xFF, (byte) 0xDA}, 40)), new byte[] {(byte) 0xFF, (byte) 0xD9});
        byte[] photo = RecuRequests.avecSegment(RecuRequests.jpegProgressif(64), 0xE1, concat(exif, repeter(new byte[] {(byte) 0xFF, (byte) 0xDA}, 40)));
        byte[] motion = avecSuite(photo, concat(new byte[] {0, 0, 0, 1}, repeter(new byte[] {(byte) 0xFF, (byte) 0xDA, 7}, 100)));

        assertThat(copier(service(20000, 1, 32, LONG, LONG), photo)).isPresent();
        assertThat(copier(service(20000, 1, 32, LONG, LONG), motion)).isPresent();
    }

    /** Une fausse fin d'image dans un segment ne dispense pas du compte : s'y arrêter laisserait passer les vrais balayages qui suivent. */
    @Test
    void desBalayagesEnExcesApresUneFausseFinDImageDansUnSegmentSontRefuses() {
        byte[] fausseFin = {1, 2, (byte) 0xFF, (byte) 0xD9, 3};
        byte[] hostile = RecuRequests.avecSegment(RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(64), 100), 0xE1, fausseFin);

        assertThat(copier(service(20000, 1, 32, LONG, LONG), hostile)).isEmpty();
    }

    /** Un flux dont on mesure ce que la copie en lit. */
    private static final class CountedPhoto extends MockMultipartFile {

        private final java.util.List<long[]> reads = new java.util.ArrayList<>();

        CountedPhoto(byte[] content) {
            super("photos", content);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            long[] tally = new long[1];
            reads.add(tally);
            return new ByteArrayInputStream(getBytes()) {
                @Override
                public synchronized int read() {
                    int value = super.read();
                    tally[0] += value >= 0 ? 1 : 0;
                    return value;
                }

                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    int read = super.read(buffer, offset, length);
                    tally[0] += Math.max(read, 0);
                    return read;
                }
            };
        }

        int opened() {
            return reads.size();
        }

        long mostRead() {
            return reads.stream().mapToLong(tally -> tally[0]).max().orElse(0);
        }
    }

    /** Le point de sécurité du parcours : un fichier hostile entier collé après une vraie fin d'image n'est ni lu ni décodé, la copie est celle de l'image de tête. */
    @Test
    void unFichierHostileColleApresLaFinDImageNEstNiLuNiDecode() {
        byte[] tete = RecuRequests.jpegProgressif(512);
        byte[] queue = RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(64), 20_000);
        CountedPhoto seule = new CountedPhoto(tete);
        CountedPhoto collee = new CountedPhoto(concat(tete, queue));
        PhotoCopyService service = service(20000, 1, 32, LONG, LONG);

        Optional<byte[]> copieSeule = service.copy(seule, PhotoFormat.JPEG);
        Optional<byte[]> copieCollee = assertTimeoutPreemptively(ofSeconds(5), () -> service.copy(collee, PhotoFormat.JPEG));

        assertThat(queue.length).isGreaterThan(190_000);
        assertThat(copieCollee).isPresent().hasValue(copieSeule.orElseThrow());
        assertThat(collee.mostRead()).isLessThan(tete.length + 32_768);
    }

    /** Un flux de tables seules (SOI EOI) devant une image hostile : le décodeur le saute et décode l'image suivante. Le garde refuse après 4 octets, avant tout autre lecteur. */
    @Test
    void uneImageHostilePrecedeeDUnFluxDeTablesSeulesEstRefuseeSansLaDecoder() {
        byte[] hostile = RecuRequests.avecBalayagesVides(RecuRequests.jpegProgressif(64), 20_000);
        CountedPhoto photo = new CountedPhoto(concat(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}, hostile));

        Optional<byte[]> copie = assertTimeoutPreemptively(ofSeconds(5), () -> service(20000, 1, 32, LONG, LONG).copy(photo, PhotoFormat.JPEG));

        assertThat(copie).isEmpty();
        assertThat(photo.opened()).isEqualTo(1);
        assertThat(photo.mostRead()).isLessThanOrEqualTo(8192);
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

    /** Un JVM dont le dossier temporaire n'existe pas : tout cache de fichier de l'encodage échouerait, la copie réussit donc seulement si elle reste en mémoire (la photo d'essai est écrite par le parent). */
    @Test
    void laCopieNeTransitePasParLeDisque(@TempDir Path dossier) throws Exception {
        Path photo = Files.write(dossier.resolve("photo.jpg"), RecuRequests.jpeg(8));
        Process enfant = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.io.tmpdir=" + dossier.resolve("inexistant"), "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                PhotoCopyDiskProbe.class.getName(), photo.toString()).redirectErrorStream(true).start();
        String sortie = new String(enfant.getInputStream().readAllBytes());

        assertThat(enfant.waitFor()).as(sortie).isZero();
    }

    /** Lancé dans un autre JVM par {@link #laCopieNeTransitePasParLeDisque}. */
    public static final class PhotoCopyDiskProbe {

        public static void main(String[] args) throws IOException {
            System.exit(copier(service(20000, 1, 32, LONG, LONG), Files.readAllBytes(Path.of(args[0]))).isPresent() ? 0 : 1);
        }
    }
}
