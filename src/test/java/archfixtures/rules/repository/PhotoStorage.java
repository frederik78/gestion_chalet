package archfixtures.rules.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Violation volontaire : le stockage lui-même retire un fichier hors de {@code discard}. */
public class PhotoStorage {

    public void purgeOld(Path photo) throws IOException {
        Files.deleteIfExists(photo);
    }
}
