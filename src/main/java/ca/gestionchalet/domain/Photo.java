package ca.gestionchalet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Une photo d'un reçu : son fichier est stocké hors de la base, sous un nom généré par le serveur. */
@Entity
@Table(name = "photo")
public class Photo {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int rang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoFormat format;

    @Column(nullable = false)
    private long taille;

    protected Photo() {
    }

    private Photo(UUID id, int rang, PhotoFormat format, long taille) {
        this.id = id;
        this.rang = rang;
        this.format = format;
        this.taille = taille;
    }

    public static Photo of(int rang, PhotoFormat format, long taille) {
        return new Photo(UUID.randomUUID(), rang, format, taille);
    }

    public UUID getId() {
        return id;
    }

    public int getRang() {
        return rang;
    }

    public PhotoFormat getFormat() {
        return format;
    }

    public long getTaille() {
        return taille;
    }
}
