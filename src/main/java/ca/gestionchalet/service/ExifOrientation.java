package ca.gestionchalet.service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Lit l'orientation Exif d'un JPEG, que l'API d'image du JDK n'expose pas ; rien d'autre n'est conservé du segment. Le fichier est hostile : un en-tête invalide ou coupé
 * vaut « pas d'orientation », et chaque segment est borné par sa longueur sur 16 bits.
 */
final class ExifOrientation {

    static final int NONE = 1;

    private static final int MARKER_PREFIX = 0xFF;
    private static final int START_OF_IMAGE = 0xD8;
    private static final int APP1 = 0xE1;
    private static final int START_OF_SCAN = 0xDA;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final int TIFF_MAGIC = 42;
    private static final int ORIENTATION_TAG = 0x0112;
    private static final int IFD_ENTRY_LENGTH = 12;
    private static final int ENTRY_VALUE_OFFSET = 8;
    private static final int MAX_ORIENTATION = 8;

    private ExifOrientation() {
    }

    static int of(InputStream jpeg) {
        try {
            DataInputStream content = new DataInputStream(jpeg);
            if (content.readUnsignedByte() != MARKER_PREFIX || content.readUnsignedByte() != START_OF_IMAGE) {
                return NONE;
            }
            while (true) {
                if (content.readUnsignedByte() != MARKER_PREFIX) {
                    return NONE;
                }
                int marker = content.readUnsignedByte();
                if (marker == START_OF_SCAN) {
                    return NONE;
                }
                byte[] segment = content.readNBytes(content.readUnsignedShort() - 2);
                if (marker == APP1 && startsWithExif(segment)) {
                    return parseTiff(ByteBuffer.wrap(segment, EXIF_HEADER.length, segment.length - EXIF_HEADER.length).slice());
                }
            }
        } catch (IOException | RuntimeException e) {
            return NONE;
        }
    }

    private static boolean startsWithExif(byte[] segment) {
        return segment.length >= EXIF_HEADER.length && Arrays.equals(segment, 0, EXIF_HEADER.length, EXIF_HEADER, 0, EXIF_HEADER.length);
    }

    private static int parseTiff(ByteBuffer tiff) {
        String order = new String(new byte[] {tiff.get(0), tiff.get(1)}, StandardCharsets.US_ASCII);
        tiff.order("MM".equals(order) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        if (!"MM".equals(order) && !"II".equals(order) || tiff.getShort(2) != TIFF_MAGIC) {
            return NONE;
        }
        int directory = tiff.getInt(4);
        int entries = Short.toUnsignedInt(tiff.getShort(directory));
        for (int i = 0; i < entries; i++) {
            int entry = directory + 2 + i * IFD_ENTRY_LENGTH;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == ORIENTATION_TAG) {
                int orientation = Short.toUnsignedInt(tiff.getShort(entry + ENTRY_VALUE_OFFSET));
                return orientation >= NONE && orientation <= MAX_ORIENTATION ? orientation : NONE;
            }
        }
        return NONE;
    }
}
