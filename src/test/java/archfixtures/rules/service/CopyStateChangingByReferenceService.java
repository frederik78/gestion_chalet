package archfixtures.rules.service;

import ca.gestionchalet.domain.Recu;
import java.util.List;

/** Violation volontaire : la transition est appelée par une référence de méthode, que {@code callMethod} ne voit pas. */
public class CopyStateChangingByReferenceService {

    public void resume(List<Recu> recus) {
        recus.forEach(Recu::reprendre);
    }
}
