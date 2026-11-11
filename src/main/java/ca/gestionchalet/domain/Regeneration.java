package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.InstantTextConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * L'état de la régénération du Sheet, unique pour l'application (section 4.2 du PRD). Il change par les transitions du tableau, les seules possibles : tout autre départ lève
 * {@link IllegalStateException}. Le serveur seul l'établit : il n'est jamais lu d'une requête ni d'une réponse de tiers.
 */
@Entity
@Table(name = "regeneration")
public class Regeneration {

    public static final int UNIQUE_ID = 1;

    @Id
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtatRegeneration etat;

    @Enumerated(EnumType.STRING)
    private MotifEchec motif;

    private Integer anneeEnCause;

    @Convert(converter = InstantTextConverter.class)
    private Instant demandeeLe;

    @Convert(converter = InstantTextConverter.class)
    private Instant derniereReussiteLe;

    @Convert(converter = InstantTextConverter.class)
    private Instant dernierEchecLe;

    private Integer recusEcrits;

    @ElementCollection
    @CollectionTable(name = "regeneration_annee_non_configuree", joinColumns = @JoinColumn(name = "regeneration_id"))
    @Column(name = "annee")
    private Set<Integer> anneesNonConfigurees = new TreeSet<>();

    protected Regeneration() {
    }

    /** Aucune régénération n'a été demandée. */
    public static Regeneration jamais() {
        Regeneration regeneration = new Regeneration();
        regeneration.id = UNIQUE_ID;
        regeneration.etat = EtatRegeneration.JAMAIS;
        return regeneration;
    }

    /** {@code JAMAIS}, {@code REUSSIE} ou {@code ECHEC} vers {@code EN_COURS} : le bouton est cliqué, aucune régénération n'est en cours. */
    public void demander(Instant maintenant) {
        requireState("EN_COURS", EtatRegeneration.JAMAIS, EtatRegeneration.REUSSIE, EtatRegeneration.ECHEC);
        etat = EtatRegeneration.EN_COURS;
        motif = null;
        anneeEnCause = null;
        demandeeLe = maintenant;
    }

    /**
     * {@code EN_COURS} vers {@code REUSSIE} : tous les onglets générés sont remplacés.
     *
     * @param anneesSansFichier les années qui ont des reçus mais aucun fichier configuré, ignorés sans échec (Q-22)
     */
    public void reussir(Instant maintenant, int recusEcrits, Collection<Integer> anneesSansFichier) {
        requireState("REUSSIE", EtatRegeneration.EN_COURS);
        etat = EtatRegeneration.REUSSIE;
        derniereReussiteLe = maintenant;
        this.recusEcrits = recusEcrits;
        retenirAnneesSansFichier(anneesSansFichier);
    }

    /**
     * {@code EN_COURS} vers {@code ECHEC} : un motif est constaté ; la date de la dernière réussite et le nombre de reçus écrits restent ceux de cette réussite.
     *
     * @param anneeEnCause l'année du fichier en cause, ou {@code null}
     */
    public void echouer(Instant maintenant, MotifEchec motif, Integer anneeEnCause, Collection<Integer> anneesSansFichier) {
        requireState("ECHEC", EtatRegeneration.EN_COURS);
        etat = EtatRegeneration.ECHEC;
        this.motif = motif;
        this.anneeEnCause = anneeEnCause;
        dernierEchecLe = maintenant;
        retenirAnneesSansFichier(anneesSansFichier);
    }

    private void retenirAnneesSansFichier(Collection<Integer> annees) {
        anneesNonConfigurees.clear();
        anneesNonConfigurees.addAll(annees);
    }

    private void requireState(String target, EtatRegeneration... allowed) {
        if (!List.of(allowed).contains(etat)) {
            throw new IllegalStateException("Transition refusée : " + etat + " vers " + target);
        }
    }

    public EtatRegeneration getEtat() {
        return etat;
    }

    public MotifEchec getMotif() {
        return motif;
    }

    public Integer getAnneeEnCause() {
        return anneeEnCause;
    }

    public Instant getDemandeeLe() {
        return demandeeLe;
    }

    public Instant getDerniereReussiteLe() {
        return derniereReussiteLe;
    }

    public Instant getDernierEchecLe() {
        return dernierEchecLe;
    }

    public Integer getRecusEcrits() {
        return recusEcrits;
    }

    public List<Integer> getAnneesNonConfigurees() {
        return List.copyOf(anneesNonConfigurees);
    }
}
