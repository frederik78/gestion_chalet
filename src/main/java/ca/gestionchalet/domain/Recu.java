package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.InstantTextConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un reçu enregistré est toujours validé : il naît à la validation, à l'état {@code A_COPIER}, quoi que dise le client. Seul il change l'état de sa copie, par les
 * transitions de la section 4.2 du PRD, les seules possibles : tout autre départ lève {@link IllegalStateException}. Seul le cycle de la copie les appelle (règle d'architecture).
 */
@Entity
@Table(name = "recu")
public class Recu {

    private static final int FIRST_VERSION = 1;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID cleIdempotence;

    @Embedded
    private Valeurs valeurs;

    /** Empreinte du contenu validé : valeurs et photos, sans le total lu, qui n'est pas saisi par l'utilisateur (R-11). */
    @Column(nullable = false)
    private String empreinteContenu;

    private Long montantTotalLuCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtatCopie etatCopie;

    @Enumerated(EnumType.STRING)
    private MotifBlocage motifBlocage;

    @Column(nullable = false)
    private int version;

    @Convert(converter = InstantTextConverter.class)
    private Instant corrigeLe;

    @Convert(converter = InstantTextConverter.class)
    @Column(nullable = false)
    private Instant valideLe;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "recu_id", nullable = false)
    @OrderBy("rang")
    private List<Photo> photos = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recu_alerte", joinColumns = @JoinColumn(name = "recu_id"))
    @OrderColumn(name = "rang")
    private List<Alerte> alertes = new ArrayList<>();

    protected Recu() {
    }

    private Recu(UUID cleIdempotence, Valeurs valeurs, String empreinteContenu, Long montantTotalLuCents, Instant valideLe, List<Photo> photos, List<Alerte> alertes) {
        this.id = UUID.randomUUID();
        this.cleIdempotence = cleIdempotence;
        this.valeurs = valeurs;
        this.empreinteContenu = empreinteContenu;
        this.montantTotalLuCents = montantTotalLuCents;
        this.etatCopie = EtatCopie.A_COPIER;
        this.version = FIRST_VERSION;
        this.valideLe = valideLe;
        this.photos.addAll(photos);
        this.alertes.addAll(alertes);
    }

    public static Recu valider(UUID cleIdempotence, Valeurs valeurs, String empreinteContenu, Long montantTotalLuCents, Instant valideLe, List<Photo> photos, List<Alerte> alertes) {
        return new Recu(cleIdempotence, valeurs, empreinteContenu, montantTotalLuCents, valideLe, photos, alertes);
    }

    /** {@code A_COPIER} vers {@code BLOQUEE} : un motif de blocage est constaté, la copie attend une action de l'utilisateur. */
    public void bloquer(MotifBlocage motif) {
        requireState("BLOQUEE", EtatCopie.A_COPIER);
        etatCopie = EtatCopie.BLOQUEE;
        motifBlocage = motif;
    }

    /** {@code BLOQUEE} vers {@code A_COPIER} : le blocage est levé, la copie est à refaire. */
    public void reprendre() {
        requireState("A_COPIER", EtatCopie.BLOQUEE);
        etatCopie = EtatCopie.A_COPIER;
        motifBlocage = null;
    }

    /**
     * {@code A_COPIER} vers {@code COPIEE} : photos déposées et ligne écrite, mais seulement si la version n'a pas changé depuis le début de la copie.
     *
     * @return faux si la version a changé : le reçu reste {@code A_COPIER}, à recopier
     */
    public boolean terminerCopie(int versionCopiee) {
        requireState("COPIEE", EtatCopie.A_COPIER);
        if (versionCopiee != version) {
            return false;
        }
        etatCopie = EtatCopie.COPIEE;
        return true;
    }

    /** {@code A_COPIER} vers {@code A_COPIER} : panne passagère, nouvel essai. */
    public void reessayer() {
        requireState("A_COPIER", EtatCopie.A_COPIER);
    }

    /** Vers {@code A_COPIER}, depuis tout état : une correction change la version, et la copie en cours, s'il y en a une, est à refaire (section 8). */
    public void corriger() {
        version++;
        etatCopie = EtatCopie.A_COPIER;
        motifBlocage = null;
    }

    /**
     * Une correction (R-29) : les valeurs et les alertes sont remplacées, la version avance, la date de correction est notée. Les photos, le montant total lu et l'empreinte du contenu
     * validé (celle de la validation, que rejoue un renvoi) restent ce qu'ils sont. Elle ne touche pas à l'état de la copie : le Sheet ne reçoit rien (D-66).
     *
     * @throws StaleVersionException si la version attendue n'est pas la courante
     */
    public void appliquerCorrection(int versionAttendue, Valeurs nouvellesValeurs, List<Alerte> nouvellesAlertes, Instant corrigeLe) {
        if (versionAttendue != version) {
            throw new StaleVersionException();
        }
        this.valeurs = nouvellesValeurs;
        this.alertes.clear();
        this.alertes.addAll(nouvellesAlertes);
        this.version++;
        this.corrigeLe = corrigeLe;
    }

    private void requireState(String target, EtatCopie... allowed) {
        if (!List.of(allowed).contains(etatCopie)) {
            throw new IllegalStateException("Transition refusée : " + etatCopie + " vers " + target);
        }
    }

    public boolean hasContent(String empreinte) {
        return empreinteContenu.equals(empreinte);
    }

    public UUID getId() {
        return id;
    }

    public Valeurs getValeurs() {
        return valeurs;
    }

    public Long getMontantTotalLuCents() {
        return montantTotalLuCents;
    }

    public EtatCopie getEtatCopie() {
        return etatCopie;
    }

    public MotifBlocage getMotifBlocage() {
        return motifBlocage;
    }

    public int getVersion() {
        return version;
    }

    public Instant getValideLe() {
        return valideLe;
    }

    public Instant getCorrigeLe() {
        return corrigeLe;
    }

    public List<Alerte> getAlertes() {
        return List.copyOf(alertes);
    }

    public List<Photo> getPhotos() {
        return List.copyOf(photos);
    }
}
