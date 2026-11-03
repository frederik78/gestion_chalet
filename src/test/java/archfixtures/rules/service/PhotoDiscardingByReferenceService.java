package archfixtures.rules.service;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;
import java.util.List;

/** Violation volontaire : retire des photos par une référence de méthode et non par un appel. */
public class PhotoDiscardingByReferenceService {

    public void purge(PhotoStorage storage, List<Photo> photos) {
        photos.forEach(storage::discard);
    }
}
