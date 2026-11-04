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

    public EtatAutorisationGoogle getEtat() {
        return etat;
    }
}
