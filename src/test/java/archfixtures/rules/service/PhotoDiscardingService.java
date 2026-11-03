package archfixtures.rules.service;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;

/** Violation volontaire : retire le fichier d'une photo hors de la validation en échec. */
public class PhotoDiscardingService {

    public void purge(PhotoStorage storage, Photo photo) {
        storage.discard(photo);
    }
}
