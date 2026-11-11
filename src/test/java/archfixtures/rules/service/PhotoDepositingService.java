package archfixtures.rules.service;

import ca.gestionchalet.domain.Photo;

/** Violation volontaire : un service autre que le cycle de la copie marque une photo comme déposée. */
public class PhotoDepositingService {

    public void deposit(Photo photo) {
        photo.deposer("fichier");
    }
}
