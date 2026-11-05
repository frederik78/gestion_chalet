package ca.gestionchalet.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Compte les paires d'octets {@code FF DA} d'un JPEG, sans interpréter ses segments. Le décodeur a besoin de ces deux octets pour voir un balayage, et le bourrage ({@code FF 00})
 * les exclut des données compressées : le compte est une borne haute des balayages, sans grammaire à contourner. Un suivi des segments, lui, se contourne : un marqueur autonome
 * {@code FF 01} lu comme un segment à longueur cachait des milliers de balayages (revue de 001-03). Une miniature Exif ou un MPF y ajoutent les leurs ; un refus à tort mène à la saisie à la main.
 */
final class JpegScanCounter {

    private static final int MARKER_PREFIX = 0xFF;
    private static final int START_OF_SCAN = 0xDA;

    private JpegScanCounter() {
    }

    /** S'arrête dès que le compte dépasse {@code limit}. */
    static boolean exceeds(InputStream jpeg, int limit) throws IOException {
        InputStream content = new BufferedInputStream(jpeg);
        int scans = 0;
        int previous = 0;
        for (int current = content.read(); current >= 0; current = content.read()) {
            if (previous == MARKER_PREFIX && current == START_OF_SCAN && ++scans > limit) {
                return true;
            }
            previous = current;
        }
        return false;
    }
}
