package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.domain.ImageTooLargeException;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
import ca.gestionchalet.support.RecuRequests;
import com.sun.management.ThreadMXBean;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class PhotoValidationServiceTest {

    private static PhotoValidationService service(List<String> formats) {
        return new PhotoValidationService(new RecuRules(List.of("Fournitures"), 5, 1_000_000L, formats, 64L));
    }

    private static PhotoValidationService service(long megapixels) {
        return new PhotoValidationService(new RecuRules(List.of("Fournitures"), 5, 1_000_000L, List.of("image/jpeg", "image/png"), megapixels));
    }

    private static MultipartFile fichier(byte[] octets) {
        return new MockMultipartFile("photos", "x", "application/octet-stream", octets);
    }

    @Test
    void ca69_unFormatRetireDeLaConfigurationEstRefuseMemeSiSonContenuEstValide() {
        PhotoValidationService pngSeul = service(List.of("image/png"));

        assertThat(pngSeul.check(List.of(fichier(RecuRequests.png(4))))).containsExactly(PhotoFormat.PNG);
        assertThatThrownBy(() -> pngSeul.check(List.of(fichier(RecuRequests.jpeg(4))))).isInstanceOf(UnsupportedPhotoFormatException.class);
    }

    @Test
    void ca69_unFormatQueLeServeurNeSaitPasReconnaitreNePeutPasEtreAnnonce() {
        assertThatThrownBy(() -> service(List.of("image/jpeg", "image/gif")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Format de photo inconnu dans recus.formats : image/gif");
    }

    /** La limite vient de la configuration : à 1 Mpx, 1000 x 1000 passe et une ligne de plus est refusée. */
    @Test
    void cs11_laLimiteDeDimensionsSuitLaConfiguration() {
        PhotoValidationService unMegapixel = service(1L);

        assertThat(unMegapixel.check(List.of(fichier(RecuRequests.entetePng(1000, 1000))))).containsExactly(PhotoFormat.PNG);
        assertThatThrownBy(() -> unMegapixel.check(List.of(fichier(RecuRequests.entetePng(1000, 1001))))).isInstanceOf(ImageTooLargeException.class);
    }

    /** Un flux qui ne finit jamais de fournir des octets, et les compte. */
    private static final class CountingEndlessStream extends InputStream {

        private final byte[] debut;
        private long lus;

        CountingEndlessStream(byte[] debut) {
            this.debut = debut;
        }

        @Override
        public int read() {
            int octet = lus < debut.length ? debut[(int) lus] & 0xFF : 0;
            lus++;
            return octet;
        }
    }

    private static final class CountingFile extends MockMultipartFile {

        private final CountingEndlessStream flux;

        CountingFile(byte[] debut) {
            super("photos", "x", "image/png", debut);
            flux = new CountingEndlessStream(debut);
        }

        @Override
        public InputStream getInputStream() {
            return flux;
        }
    }

    /**
     * 192 Mo de pixels si l'image était décodée (3 octets x 8000 x 8001), et un flux sans fin : le refus vient de l'en-tête, avec une
     * lecture bornée et moins de 8 Mo alloués.
     */
    private static Stream<Arguments> entetesEnormes() {
        return Stream.of(Arguments.of("PNG", RecuRequests.entetePng(8000, 8001)), Arguments.of("JPEG", RecuRequests.jpegAnnoncant(8000, 8001)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entetesEnormes")
    void cs11_uneImageEnormeEstRefuseeSansDecoderSesPixels(String format, byte[] entete) {
        CountingFile fichier = new CountingFile(entete);
        ThreadMXBean memoire = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long fil = Thread.currentThread().threadId();
        service(64L).check(List.of(fichier(RecuRequests.png(1))));
        long alloueAvant = memoire.getThreadAllocatedBytes(fil);

        assertThatThrownBy(() -> service(64L).check(List.of(fichier))).isInstanceOf(ImageTooLargeException.class);

        assertThat(memoire.getThreadAllocatedBytes(fil) - alloueAvant).isLessThan(8L * 1024 * 1024);
        assertThat(fichier.flux.lus).isLessThan(64L * 1024);
    }
}
