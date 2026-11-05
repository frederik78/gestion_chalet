package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Le parcours de la structure d'un JPEG, sur des fichiers écrits octet par octet. Il relève de la section 7.3 du PRD et de la revue de 001-03, sans critère propre. Le décodeur
 * n'intervient pas : seul le garde est en jeu, et chaque règle du parcours a son test.
 */
class JpegScanCounterTest {

    private static final int LIMIT = 32;
    private static final byte[] SOI = bytes(0xFF, 0xD8);
    private static final byte[] EOI = bytes(0xFF, 0xD9);
    private static final byte[] SOF = bytes(0xFF, 0xC0, 0, 11, 8, 0, 8, 0, 8, 1, 1, 0x11, 0);
    /** L'en-tête d'un balayage : longueur 8, un composant. */
    private static final byte[] SOS = bytes(0xFF, 0xDA, 0, 8, 1, 1, 0, 0, 63, 0);
    private static final byte[] DATA = bytes(0x12, 0x34, 0x56);

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            result.writeBytes(part);
        }
        return result.toByteArray();
    }

    private static byte[] repeat(byte[] motif, int times) {
        byte[][] parts = new byte[times][];
        Arrays.fill(parts, motif);
        return join(parts);
    }

    private static byte[] segment(int marker, byte[] content) {
        return join(bytes(0xFF, marker, (content.length + 2) >> 8, (content.length + 2) & 0xFF), content);
    }

    /** Des balayages au nombre voulu, suivis de données compressées, {@code separator} figurant entre les données d'un balayage et le balayage suivant. */
    private static byte[] scans(int count, byte[] separator) {
        return join(SOS, DATA, repeat(join(separator, SOS, DATA), count - 1));
    }

    private static byte[] jpeg(byte[]... parts) {
        return join(SOI, join(parts), EOI);
    }

    private static boolean refuses(byte[] jpeg) {
        return verdict(jpeg) != JpegScanCounter.Verdict.ACCEPTED;
    }

    private static JpegScanCounter.Verdict verdict(byte[] jpeg) {
        try {
            return JpegScanCounter.check(new ByteArrayInputStream(jpeg), LIMIT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Le journal distingue les deux causes : ce sont les refus à tort qui ont causé l'incident. */
    @Test
    void leVerdictDistingueTropDeBalayagesEtStructureInattendue() {
        assertThat(verdict(jpeg(SOF, scans(1, new byte[0])))).isEqualTo(JpegScanCounter.Verdict.ACCEPTED);
        assertThat(verdict(jpeg(SOF, scans(LIMIT + 1, new byte[0])))).isEqualTo(JpegScanCounter.Verdict.TOO_MANY_SCANS);
        assertThat(verdict(join(SOI, SOI, SOF, scans(1, new byte[0]), EOI))).isEqualTo(JpegScanCounter.Verdict.UNEXPECTED_STRUCTURE);
        assertThat(verdict(join(SOI, EOI))).isEqualTo(JpegScanCounter.Verdict.UNEXPECTED_STRUCTURE);
        assertThat(verdict(jpeg(bytes(0xFF, 0xE9, 0, 100, 1), SOF))).isEqualTo(JpegScanCounter.Verdict.UNEXPECTED_STRUCTURE);
    }

    @Test
    void unJpegOrdinaireAUnBalayageEstAccepte() {
        assertThat(refuses(jpeg(SOF, scans(1, new byte[0])))).isFalse();
    }

    /** Une photo Motion : des FF DA et une fausse fin d'image dans une miniature, puis une carte de gain et une vidéo après la vraie fin. */
    @Test
    void desBalayagesEtUneFinDImageDansUnSegmentOuApresLaFinNeComptentPas() {
        byte[] miniature = join(repeat(bytes(0xFF, 0xDA), 40), EOI, repeat(bytes(0xFF, 0xDA), 40));
        byte[] photo = join(jpeg(segment(0xE1, miniature), SOF, scans(1, new byte[0])), repeat(join(SOS, DATA), 100), repeat(bytes(0xFF, 0xDA), 100));

        assertThat(refuses(photo)).isFalse();
    }

    @Test
    void laLimiteExacteEstAccepteeEtUnBalayageDeTropEstRefuse() {
        byte[][] separateurs = {new byte[0], bytes(0xFF, 0x01), bytes(0xFF, 0xD3), bytes(0xFF, 0xFF, 0xFF), bytes(0xFF, 0x00, 0xFF, 0xD0, 0xFF, 0x01, 0xFF, 0xFF)};
        for (byte[] separateur : separateurs) {
            String nom = Arrays.toString(separateur);
            assertThat(refuses(jpeg(SOF, scans(LIMIT, separateur)))).as("32 balayages, séparateur " + nom).isFalse();
            assertThat(refuses(jpeg(SOF, scans(LIMIT + 1, separateur)))).as("33 balayages, séparateur " + nom).isTrue();
        }
    }

    @Test
    void desMarqueursSansLongueurEntreLesSegmentsNeSontPasLusCommeDesSegments() {
        // Lu comme un segment, FF 01 prendrait FF C0 pour sa longueur et sauterait tout le reste.
        byte[] photo = jpeg(bytes(0xFF, 0x01), bytes(0xFF, 0xD0), bytes(0xFF, 0xD7), bytes(0xFF, 0xFF, 0xFF, 0x01), SOF, scans(1, new byte[0]));

        assertThat(refuses(photo)).isFalse();
        assertThat(refuses(jpeg(bytes(0xFF, 0x01), SOF, scans(LIMIT + 1, new byte[0])))).isTrue();
    }

    @Test
    void unSegmentEstSauteParSaLongueurEtNonLuCommeUnMarqueur() {
        byte[] contenu = join(bytes(0xFF, 0xD8, 0xFF, 0x00, 0x12), SOS, bytes(0xFF, 0xFF));

        assertThat(refuses(jpeg(segment(0xE1, contenu), SOF, scans(1, new byte[0])))).isFalse();
        assertThat(refuses(jpeg(segment(0xE1, contenu), SOF, scans(LIMIT + 1, new byte[0])))).isTrue();
    }

    @Test
    void uneFausseFinDImageDansUnSegmentNArretePasLeParcours() {
        byte[] contenu = join(bytes(1, 2), EOI, bytes(3));

        assertThat(refuses(jpeg(segment(0xE1, contenu), SOF, scans(LIMIT + 1, new byte[0])))).isTrue();
    }

    @Test
    void laVraieFinDImageArreteLeParcoursEtLaSuiteNEstPasLue() {
        byte[] suite = join(bytes(0x99, 0xFF, 0x00), scans(LIMIT + 50, new byte[0]), new byte[] {(byte) 0xFF});

        assertThat(refuses(join(jpeg(SOF, scans(1, new byte[0])), suite))).isFalse();
    }

    /** Un flux de tables seules : le décodeur du JDK le saute et décode l'image qui suit, avec tous ses balayages. Une fin d'image sans balayage vu est donc refusée. */
    @Test
    void uneFinDImageSansAucunBalayageEstRefuseeQuoiQuiSuive() {
        byte[] hostile = jpeg(SOF, scans(LIMIT + 50, new byte[0]));
        byte[] tables = segment(0xDB, new byte[65]);

        assertThat(refuses(join(SOI, EOI, hostile))).as("SOI EOI puis une image").isTrue();
        assertThat(refuses(join(SOI, tables, EOI, hostile))).as("SOI DQT EOI puis une image").isTrue();
        assertThat(refuses(join(SOI, EOI, SOI, EOI, hostile))).as("SOI EOI SOI EOI puis une image").isTrue();
        assertThat(refuses(join(SOI, EOI))).as("SOI EOI seul").isTrue();
        assertThat(refuses(join(SOI, EOI, jpeg(SOF, scans(1, new byte[0]))))).as("SOI EOI puis une image ordinaire").isTrue();
    }

    @Test
    void desDonneesCompresseesContiennentLeBourrageEtLesRedemarrages() {
        byte[] donnees = bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD3, 0x56, 0xFF, 0xD0, 0x78);

        assertThat(refuses(jpeg(SOF, SOS, donnees))).isFalse();
        assertThat(refuses(join(SOI, SOF, SOS, donnees, scans(1, new byte[0]), EOI))).isFalse();
    }

    @Test
    void unFichierCoupeDansLesDonneesCompresseesNEstRefuseQuAuDelaDeLaLimite() {
        assertThat(refuses(join(SOI, SOF, scans(LIMIT, new byte[0])))).isFalse();
        assertThat(refuses(join(SOI, SOF, scans(LIMIT, new byte[0]), bytes(0xFF)))).isFalse();
        assertThat(refuses(join(SOI, SOF, scans(LIMIT + 1, new byte[0])))).isTrue();
    }

    @Test
    void lesConstructionsInattenduesSontRefusees() {
        byte[] balayage = scans(1, new byte[0]);

        assertThat(refuses(join(SOI, SOI, SOF, balayage, EOI))).as("second D8").isTrue();
        assertThat(refuses(join(SOI, SOF, balayage, SOI, EOI))).as("D8 après un balayage").isTrue();
        assertThat(refuses(jpeg(bytes(0xFF, 0xE9, 0, 0), SOF, balayage))).as("longueur 0").isTrue();
        assertThat(refuses(jpeg(bytes(0xFF, 0xE9, 0, 1), SOF, balayage))).as("longueur 1").isTrue();
        assertThat(refuses(jpeg(bytes(0xFF, 0xE9, 0, 100, 1, 2, 3)))).as("segment au-delà de la fin").isTrue();
        assertThat(refuses(join(SOI, SOF, bytes(0xFF, 0xE9, 0, 8, 1, 2)))).as("segment coupé").isTrue();
        assertThat(refuses(join(SOI, bytes(0xFF, 0xE9, 0, 2), bytes(0x07), SOF, balayage, EOI))).as("octet parasite entre deux segments").isTrue();
        assertThat(refuses(join(SOI, bytes(0xFF, 0x00), SOF, balayage, EOI))).as("FF 00 hors balayage").isTrue();
        assertThat(refuses(join(SOF, balayage, EOI))).as("sans D8").isTrue();
        assertThat(refuses(join(bytes(0x12, 0x34), SOF, balayage, EOI))).as("deux octets quelconques à la place de D8").isTrue();
        assertThat(refuses(join(SOI, bytes(0xFF, 0xD8, 0, 2), SOF, balayage, EOI))).as("second D8 suivi d'une longueur plausible").isTrue();
        assertThat(refuses(join(SOI, bytes(0xFF, 0x00, 0, 2), SOF, balayage, EOI))).as("FF 00 suivi d'une longueur plausible").isTrue();
        assertThat(refuses(join(SOI, SOF, bytes(0xFF, 0xDA, 0, 8, 1)))).as("en-tête de balayage coupé").isTrue();
        assertThat(refuses(join(SOI, SOF, bytes(0xFF, 0xE9, 0, 2)))).as("fin du fichier là où un marqueur est attendu").isTrue();
        assertThat(refuses(SOI)).as("D8 seul").isTrue();
        assertThat(refuses(new byte[0])).as("vide").isTrue();
        assertThat(refuses(join(SOI, SOF, balayage, bytes(0xFF, 0xD8), EOI))).as("D8 dans les données").isTrue();
    }
}
