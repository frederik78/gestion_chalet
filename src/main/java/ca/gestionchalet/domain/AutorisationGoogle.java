package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** L'autorisation Google, unique pour l'application. Ses jetons n'existent ici que chiffrés ; sans ligne en base, l'état est {@code ABSENTE}. */
@Entity
@Table(name = "autorisation_google")
public class AutorisationGoogle {

    public static final int UNIQUE_ID = 1;

    @Id
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtatAutorisationGoogle etat;

    private String jetonAccesChiffre;

    @Convert(converter = InstantTextConverter.class)
    private Instant jetonAccesExpireLe;

    private String jetonRenouvellementChiffre;

    protected AutorisationGoogle() {
    }

    public static AutorisationGoogle absente() {
        AutorisationGoogle autorisation = new AutorisationGoogle();
        autorisation.id = UNIQUE_ID;
        autorisation.etat = EtatAutorisationGoogle.ABSENTE;
        return autorisation;
    }

    /** Tous les droits sont accordés : les jetons, déjà chiffrés, remplacent les précédents. */
    public void accorder(String jetonAcces, Instant expireLe, String jetonRenouvellement) {
        etat = EtatAutorisationGoogle.VALIDE;
        jetonAccesChiffre = jetonAcces;
        jetonAccesExpireLe = expireLe;
        jetonRenouvellementChiffre = jetonRenouvellement;
    }

    /** Droits incomplets, expiration ou révocation : aucun jeton n'est gardé, une nouvelle autorisation est nécessaire. */
    public void demanderRenouvellement() {
        etat = EtatAutorisationGoogle.A_RENOUVELER;
        jetonAccesChiffre = null;
        jetonAccesExpireLe = null;
        jetonRenouvellementChiffre = null;
    }

    /** Google refuse le renouvellement sans avoir révoqué le jeton ({@code invalid_client}, par exemple) : il reste, pour reprendre sans nouveau consentement une fois la cause corrigée (D-57). */
    public void suspendre() {
        etat = EtatAutorisationGoogle.A_RENOUVELER;
    }

    /** Un jeton de renouvellement est encore là : autorisation valide, ou suspendue. */
    public boolean estRenouvelable() {
        return jetonRenouvellementChiffre != null;
    }

    public EtatAutorisationGoogle getEtat() {
        return etat;
    }

    public String getJetonAccesChiffre() {
        return jetonAccesChiffre;
    }

    public Instant getJetonAccesExpireLe() {
        return jetonAccesExpireLe;
    }

    public String getJetonRenouvellementChiffre() {
        return jetonRenouvellementChiffre;
    }
}
