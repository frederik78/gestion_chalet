package ca.gestionchalet.domain;

import java.util.Arrays;
import java.util.Optional;

/** Formats de photo pris en charge et leur extension, pour un nom de fichier généré par le serveur. */
public enum PhotoFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String mediaType;
    private final String extension;

    PhotoFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<PhotoFormat> ofMediaType(String mediaType) {
        return Arrays.stream(values()).filter(format -> format.mediaType.equals(mediaType)).findFirst();
    }
}
