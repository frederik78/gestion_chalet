package archfixtures.rules.service;

import java.io.File;

/** Violation volontaire : une purge par {@code java.io.File}. */
public class LegacyFileDeletingService {

    public boolean purge(File photo) {
        return photo.delete();
    }
}
