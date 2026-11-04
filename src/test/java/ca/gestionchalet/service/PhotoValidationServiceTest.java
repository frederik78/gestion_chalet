package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
import ca.gestionchalet.support.RecuRequests;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class PhotoValidationServiceTest {

    private static PhotoValidationService service(List<String> formats) {
        return new PhotoValidationService(new RecuRules(List.of("Fournitures"), 5, 1_000_000L, formats));
    }

    private static MultipartFile fichier(byte[] octets) {
        return new MockMultipartFile("photos", "x", "application/octet-stream", octets);
    }

    @Test
    void ca69_unFormatRetireDeLaConfigurationEstRefuseMemeSiSonContenuEstValide() {
        PhotoValidationService pngSeul = service(List.of("image/png"));

        assertThat(pngSeul.check(List.of(fichier(RecuRequests.png(4))))).containsExactly(PhotoFormat.PNG);
        assertThatThrownBy(() -> pngSeul.check(List.of(fichier(RecuRequests.jpeg(4))))).isInstanceOf(UnsupportedPhotoFormatException.class);
    }

    @Test
    void ca69_unFormatQueLeServeurNeSaitPasReconnaitreNePeutPasEtreAnnonce() {
        assertThatThrownBy(() -> service(List.of("image/jpeg", "image/gif")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Format de photo inconnu dans recus.formats : image/gif");
    }
}
