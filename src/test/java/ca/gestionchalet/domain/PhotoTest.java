package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhotoTest {

    @Test
    void ca45_uneNouvellePhotoNEstPasDansDrive() {
        assertThat(Photo.of(1, PhotoFormat.PNG, 10L).getDriveFileId()).isNull();
    }

    @Test
    void ca45_lePremierFichierDriveEstGardeEtLeMemeFichierRedonneApresUneRepriseEstAccepte() {
        Photo photo = Photo.of(1, PhotoFormat.PNG, 10L);

        photo.deposer("fichier-a");
        photo.deposer("fichier-a");

        assertThat(photo.getDriveFileId()).isEqualTo("fichier-a");
    }

    @Test
    void ca45_unAutreFichierDriveSignaleraitUnDoublonEtEstRefuse() {
        Photo photo = Photo.of(1, PhotoFormat.PNG, 10L);
        photo.deposer("fichier-a");

        assertThatThrownBy(() -> photo.deposer("fichier-b")).isInstanceOf(IllegalStateException.class);
        assertThat(photo.getDriveFileId()).isEqualTo("fichier-a");
    }
}
