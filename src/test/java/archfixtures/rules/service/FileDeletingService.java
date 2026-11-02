package archfixtures.rules.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Violation volontaire : une purge de fichiers hors du stockage des photos. */
public class FileDeletingService {

    public void purge(Path photo) throws IOException {
        Files.delete(photo);
    }
}
