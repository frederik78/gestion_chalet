package archfixtures.rules.service;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;

/** Violation volontaire : la classe exemptée retire une photo hors du retour de transaction. */
public class RecuService {

    public void discardNow(PhotoStorage storage, Photo photo) {
        storage.discard(photo);
    }
}
