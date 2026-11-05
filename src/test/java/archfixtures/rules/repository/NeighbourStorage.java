package archfixtures.rules.repository;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;

/** Violation volontaire : une voisine de {@code PhotoStorage} dans le même package retire une photo. */
public class NeighbourStorage {

    public void purge(PhotoStorage storage, Photo photo) {
        storage.discard(photo);
    }
}
