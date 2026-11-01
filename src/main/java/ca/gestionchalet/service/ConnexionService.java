package ca.gestionchalet.service;

import ca.gestionchalet.security.CompteLocal;
import ca.gestionchalet.security.IdentifiantsInvalidesException;
import ca.gestionchalet.security.LimiteurConnexion;
import ca.gestionchalet.security.TropDeTentativesException;
import org.springframework.stereotype.Service;

/** Vérifie une tentative de connexion : une source bloquée n'est même pas comparée au mot de passe. */
@Service
public class ConnexionService {

    private final CompteLocal compte;
    private final LimiteurConnexion limiteur;

    public ConnexionService(CompteLocal compte, LimiteurConnexion limiteur) {
        this.compte = compte;
        this.limiteur = limiteur;
    }

    public void connecter(String motDePasse, String source) {
        limiteur.blocageRestant(source).ifPresent(restant -> {
            throw new TropDeTentativesException(restant);
        });
        if (!compte.verifier(motDePasse)) {
            limiteur.enregistrerEchec(source);
            throw new IdentifiantsInvalidesException();
        }
        limiteur.reinitialiser(source);
    }
}
