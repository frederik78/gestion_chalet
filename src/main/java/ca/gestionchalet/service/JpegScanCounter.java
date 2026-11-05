package ca.gestionchalet.service;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Parcourt la structure d'un JPEG comme le fait libjpeg, le décodeur du JDK, pour refuser une photo qui a trop de balayages ou une structure que le parcours ne reconnaît pas.
 * Chaque balayage fait refaire au décodeur une passe complète : des milliers de balayages vides bloquent le décodage. Un compte brut des paires {@code FF DA} refusait à tort les
 * photos réelles, car une miniature (Exif, MPF) ou la carte de gain d'une Motion Photo en contiennent aussi, hors de l'image ; le parcours, lui, ne compte que ce que le décodeur lit.
 *
 * <p>La grammaire : {@code FF D8}, puis des segments. Des octets {@code FF} répétés avant un marqueur sont du bourrage ; {@code 01} et {@code D0}-{@code D7} n'ont pas de longueur ;
 * tout autre marqueur a une longueur sur deux octets (au moins 2, elle compte ses propres octets) et son contenu est sauté, même s'il contient {@code FF DA} ou {@code FF D9}.
 * Après un balayage, les données compressées s'étendent jusqu'au prochain marqueur : {@code FF 00} et {@code FF D0}-{@code FF D7} en font partie, tout autre {@code FF xx} est le marqueur
 * suivant. Le parcours s'arrête à la vraie fin d'image {@code FF D9} : si au moins un balayage l'a précédée, ce qui suit n'est pas lu, le décodeur non plus (vérifié par {@code PhotoCopyServiceTest}). Une fin d'image sans aucun balayage est refusée : un flux de tables seules ({@code FF D8 FF D9}, ou {@code FF D8} tables {@code FF D9}) est sauté par le décodeur du JDK, qui décode l'image suivante, avec tous ses balayages (mesuré : 20 010 passes derrière un préfixe de 4 octets).
 *
 * <p>Refus par défaut : une construction inattendue (second {@code D8}, octet hors marqueur là où un marqueur est attendu, {@code FF 00} hors données compressées, longueur
 * inférieure à 2, segment qui dépasse la fin du fichier, fin du fichier entre deux segments) refuse la photo, même si libjpeg la tolère. Un fichier coupé dans des données compressées
 * n'est refusé que s'il dépasse déjà la limite : le décodeur décide du reste. Le suivi naïf des segments se contournait par un {@code FF 01} lu comme un segment (revue de 001-03) :
 * la correspondance avec le décodeur est prouvée par les tests, avec ce fichier.
 */
final class JpegScanCounter {

    private static final int MARKER_PREFIX = 0xFF;
    private static final int STUFFED_BYTE = 0x00;
    private static final int TEMPORARY = 0x01;
    private static final int RESTART_FIRST = 0xD0;
    private static final int RESTART_LAST = 0xD7;
    private static final int START_OF_IMAGE = 0xD8;
    private static final int END_OF_IMAGE = 0xD9;
    private static final int START_OF_SCAN = 0xDA;
    private static final int MIN_SEGMENT_LENGTH = 2;
    private static final int END_OF_FILE = -1;

    private JpegScanCounter() {
    }

    /** Vrai si la photo a plus de {@code limit} balayages avant sa fin d'image, ou une structure inattendue. */
    static boolean refuses(InputStream jpeg, int limit) throws IOException {
        InputStream content = new BufferedInputStream(jpeg);
        if (content.read() != MARKER_PREFIX || content.read() != START_OF_IMAGE) {
            return true;
        }
        int scans = 0;
        int marker = nextMarker(content);
        while (true) {
            if (marker == END_OF_IMAGE) {
                return scans == 0;
            }
            if (marker < 0 || marker == STUFFED_BYTE || marker == START_OF_IMAGE) {
                return true;
            }
            if (marker == TEMPORARY || marker >= RESTART_FIRST && marker <= RESTART_LAST) {
                marker = nextMarker(content);
                continue;
            }
            if (!skipSegment(content) || marker == START_OF_SCAN && ++scans > limit) {
                return true;
            }
            marker = marker == START_OF_SCAN ? markerAfterCompressedData(content) : nextMarker(content);
        }
    }

    /** Le marqueur qui suit, après le bourrage ; {@code -1} si le fichier finit ou si un octet autre que {@code FF} le précède. */
    private static int nextMarker(InputStream content) throws IOException {
        if (content.read() != MARKER_PREFIX) {
            return END_OF_FILE;
        }
        return markerAfterPrefix(content);
    }

    private static int markerAfterPrefix(InputStream content) throws IOException {
        int marker = content.read();
        while (marker == MARKER_PREFIX) {
            marker = content.read();
        }
        return marker;
    }

    /** Saute le segment dont la longueur est à lire ; faux si elle est inférieure à 2 ou si le fichier finit avant le bout du segment. */
    private static boolean skipSegment(InputStream content) throws IOException {
        int high = content.read();
        int low = content.read();
        if (low < 0) {
            return false;
        }
        int length = high << 8 | low;
        if (length < MIN_SEGMENT_LENGTH) {
            return false;
        }
        try {
            content.skipNBytes(length - MIN_SEGMENT_LENGTH);
            return true;
        } catch (EOFException e) {
            return false;
        }
    }

    /**
     * Le marqueur qui termine les données compressées. Un fichier coupé là rend {@code END_OF_IMAGE} : il n'a plus de balayage à compter, le décodeur dira s'il se décode.
     */
    private static int markerAfterCompressedData(InputStream content) throws IOException {
        for (int current = content.read(); current != END_OF_FILE; current = content.read()) {
            if (current != MARKER_PREFIX) {
                continue;
            }
            int marker = markerAfterPrefix(content);
            if (marker == END_OF_FILE) {
                return END_OF_IMAGE;
            }
            if (marker != STUFFED_BYTE && (marker < RESTART_FIRST || marker > RESTART_LAST)) {
                return marker;
            }
        }
        return END_OF_IMAGE;
    }
}
