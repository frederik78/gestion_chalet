package ca.gestionchalet.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * L'onglet généré du fichier d'une année, tel que la régénération le connaît (R-41, R-53) : l'identité de l'onglet qu'elle a créé, pour ne jamais toucher un onglet qu'elle n'a pas
 * créé, l'onglet temporaire en cours, et ce qu'elle a écrit dans l'onglet, ligne par ligne. Ce contenu est remplacé en entier, seulement à la réussite du remplacement ; avant de demander le
 * remplacement, l'onglet est noté en cours, donc incertain : si l'issue est inconnue (arrêt, réponse perdue), la trace en reste. Aucun accès n'est public hors de l'application.
 */
@Entity
@Table(name = "onglet_genere")
public class OngletGenere {

    @Id
    private int annee;

    @Column(nullable = false)
    private String fichier;

    private Integer ongletId;

    private Integer ongletTemporaireId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtatContenu etatContenu;

    @ElementCollection
    @CollectionTable(name = "onglet_genere_cellule", joinColumns = @JoinColumn(name = "annee"))
    private List<CelluleMemorisee> cellules = new ArrayList<>();

    protected OngletGenere() {
    }

    /** Un onglet jamais écrit : rien n'est mémorisé, donc rien n'est certain. */
    public static OngletGenere jamaisEcrit(int annee, String fichier) {
        OngletGenere onglet = new OngletGenere();
        onglet.annee = annee;
        onglet.fichier = fichier;
        onglet.etatContenu = EtatContenu.INCERTAIN;
        return onglet;
    }

    /** Le fichier de l'année est celui-là ; sinon, la configuration a changé et ce qui est mémorisé vise un autre fichier. */
    public boolean estDansLeFichier(String fichierConfigure) {
        return fichier.equals(fichierConfigure);
    }

    /** L'année vise un autre fichier : l'onglet, son identité et son contenu mémorisés sont oubliés. */
    public void changerDeFichier(String nouveauFichier) {
        fichier = nouveauFichier;
        ongletId = null;
        ongletTemporaireId = null;
        etatContenu = EtatContenu.INCERTAIN;
        cellules.clear();
    }

    public void noterOngletTemporaire(int sheetId) {
        ongletTemporaireId = sheetId;
    }

    public void oublierOngletTemporaire() {
        ongletTemporaireId = null;
    }

    /** Avant de demander le remplacement : l'onglet qui le recevra est connu, et ce qu'il contient est incertain tant que la réussite n'est pas connue. */
    public void annoncerRemplacement(int sheetId) {
        ongletId = sheetId;
        etatContenu = EtatContenu.INCERTAIN;
    }

    /** À la réussite du remplacement : ce qui a été écrit remplace entièrement le contenu mémorisé. */
    public void memoriser(List<CelluleMemorisee> ecrites) {
        cellules.clear();
        cellules.addAll(ecrites);
        etatContenu = EtatContenu.CERTAIN;
    }

    public Integer getOngletId() {
        return ongletId;
    }

    public Integer getOngletTemporaireId() {
        return ongletTemporaireId;
    }

    public EtatContenu getEtatContenu() {
        return etatContenu;
    }
}
