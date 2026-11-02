package ca.gestionchalet.domain;

import ca.gestionchalet.domain.converter.InstantTextConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    private Long montantTotalLuCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtatCopie etatCopie;

    @Column(nullable = false)
    private int version;

    @Convert(converter = InstantTextConverter.class)
    @Column(nullable = false)
    private Instant valideLe;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "recu_id", nullable = false)
    @OrderBy("rang")
    private List<Photo> photos = new ArrayList<>();

    protected Recu() {
    }

    private Recu(UUID cleIdempotence, Valeurs valeurs, Long montantTotalLuCents, Instant valideLe, List<Photo> photos) {
        this.id = UUID.randomUUID();
        this.cleIdempotence = cleIdempotence;
        this.valeurs = valeurs;
        this.montantTotalLuCents = montantTotalLuCents;
        this.etatCopie = EtatCopie.A_COPIER;
        this.version = FIRST_VERSION;
        this.valideLe = valideLe;
        this.photos.addAll(photos);
    }

    public static Recu valider(UUID cleIdempotence, Valeurs valeurs, Long montantTotalLuCents, Instant valideLe, List<Photo> photos) {
        return new Recu(cleIdempotence, valeurs, montantTotalLuCents, valideLe, photos);
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

    public int getVersion() {
        return version;
    }

    public Instant getValideLe() {
        return valideLe;
    }

    public List<Photo> getPhotos() {
        return List.copyOf(photos);
    }
}
