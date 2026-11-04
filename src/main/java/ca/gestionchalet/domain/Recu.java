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

/** Un reçu enregistré est toujours validé : il naît à la validation, à l'état {@code A_COPIER}, quoi que dise le client. */
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

    /** Un motif de blocage est constaté : la copie attend une action de l'utilisateur (PRD, section 4.2). */
    public void bloquer(MotifBlocage motif) {
        if (etatCopie != EtatCopie.A_COPIER) {
            throw new IllegalStateException("Transition refusée : " + etatCopie + " vers BLOQUEE");
        }
        etatCopie = EtatCopie.BLOQUEE;
        motifBlocage = motif;
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

    public List<Alerte> getAlertes() {
        return List.copyOf(alertes);
    }

    public List<Photo> getPhotos() {
        return List.copyOf(photos);
    }
}
