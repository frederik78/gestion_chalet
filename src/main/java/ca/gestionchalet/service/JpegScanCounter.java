package ca.gestionchalet.service;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Compte les balayages d'un JPEG en suivant ses segments, sans le décoder. Le contrôle doit précéder le décodage : sur 100 000 balayages vides, le décodeur natif du JDK ne rend plus la
 * main à {@code reader.abort()} (mesuré sur 8000x8000 : encore en décodage après 50 s malgré une borne de 32 passes). Un fichier mal formé s'arrête au compte atteint : le décodeur en jugera.
 */
final class JpegScanCounter {

    private static final int MARKER_PREFIX = 0xFF;
    private static final int STUFFING = 0x00;
    private static final int FIRST_RESTART = 0xD0;
    private static final int LAST_RESTART = 0xD7;
    private static final int END_OF_IMAGE = 0xD9;
    private static final int START_OF_SCAN = 0xDA;

    private JpegScanCounter() {
    }

    /** S'arrête dès que le compte dépasse {@code limit}. */
    static boolean exceeds(InputStream jpeg, int limit) throws IOException {
        DataInputStream content = new DataInputStream(new BufferedInputStream(jpeg));
        int scans = 0;
        try {
            content.skipNBytes(2);
            int marker = nextMarker(content);
            while (marker != END_OF_IMAGE) {
                content.skipNBytes(content.readUnsignedShort() - 2L);
                if (marker == START_OF_SCAN) {
                    scans++;
                    if (scans > limit) {
                        return true;
                    }
                }
                marker = nextMarker(content);
            }
        } catch (EOFException e) {
            return false;
        }
        return false;
    }

    /** Le prochain marqueur, en sautant les données d'un balayage : un 0xFF suivi de 0x00 ou d'un marqueur de reprise en fait partie. */
    private static int nextMarker(DataInputStream content) throws IOException {
        int previous = content.readUnsignedByte();
        while (true) {
            int current = content.readUnsignedByte();
            boolean marker = previous == MARKER_PREFIX && current != STUFFING && current != MARKER_PREFIX
                    && (current < FIRST_RESTART || current > LAST_RESTART);
            if (marker) {
                return current;
            }
            previous = current;
        }
    }
}
