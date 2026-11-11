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

    /** Identifiant du fichier dans Drive, tel que Drive l'a donné ; absent tant que la photo n'est pas déposée. */
    private String driveFileId;

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

    /** La photo est dans Drive. Le même fichier peut être redonné après une reprise ; un autre fichier signalerait un doublon. */
    public void deposer(String fileId) {
        if (driveFileId != null && !driveFileId.equals(fileId)) {
            throw new IllegalStateException("Photo déjà déposée sous un autre fichier");
        }
        driveFileId = fileId;
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

    public String getDriveFileId() {
        return driveFileId;
    }
}
