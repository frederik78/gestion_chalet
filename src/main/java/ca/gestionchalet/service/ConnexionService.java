package ca.gestionchalet.service;

import ca.gestionchalet.security.CompteLocal;
import ca.gestionchalet.security.IdentifiantsInvalidesException;
import org.springframework.stereotype.Service;

/** Vérifie une tentative de connexion. */
@Service
public class ConnexionService {

    private final CompteLocal compte;

    public ConnexionService(CompteLocal compte) {
        this.compte = compte;
    }

    public void connecter(String motDePasse) {
        if (!compte.verifier(motDePasse)) {
            throw new IdentifiantsInvalidesException();
        }
    }
}
