package archfixtures.rules.service;

import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Recu;

/** Violation volontaire : un service autre que le cycle de la copie change l'état de la copie d'un reçu. */
public class CopyStateChangingService {

    public void block(Recu recu) {
        recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE);
    }
}
