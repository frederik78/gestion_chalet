package archfixtures.rules.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/** Violation : aucune désérialisation d'objets Java. */
public class ObjectReadingService {
    Object lire(InputStream flux) throws IOException, ClassNotFoundException {
        return new ObjectInputStream(flux).readObject();
    }
}
