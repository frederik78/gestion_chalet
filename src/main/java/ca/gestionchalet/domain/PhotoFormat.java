package ca.gestionchalet.domain;

import java.util.Arrays;
import java.util.Optional;

/** Formats de photo pris en charge : leur signature en début de fichier, leur type de média et l'extension d'un nom généré par le serveur. */
public enum PhotoFormat {
    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    /** Le nombre d'octets à lire pour reconnaître n'importe quel format. */
    public static final int SIGNATURE_MAX_LENGTH = 8;

    private final String mediaType;
    private final String extension;
    private final byte[] signature;

    PhotoFormat(String mediaType, String extension, byte[] signature) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<PhotoFormat> ofMediaType(String mediaType) {
        return Arrays.stream(values()).filter(format -> format.mediaType.equals(mediaType)).findFirst();
    }

    /** Le format dont la signature ouvre ces octets. */
    public static Optional<PhotoFormat> ofSignature(byte[] start) {
        return Arrays.stream(values()).filter(format -> format.opens(start)).findFirst();
    }

    private boolean opens(byte[] start) {
        return start.length >= signature.length && Arrays.equals(signature, 0, signature.length, start, 0, signature.length);
    }
}
