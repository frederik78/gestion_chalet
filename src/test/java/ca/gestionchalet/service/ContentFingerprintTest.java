package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.domain.Valeurs;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ContentFingerprintTest {

    private static Valeurs valeurs() {
        return new Valeurs(LocalDate.of(2026, 10, 11), "Fournisseur", "Objet", "Fournitures", 1000L, 50L, 100L, 1150L, "123", "456");
    }

    private static MultipartFile photo(String octets) {
        return new MockMultipartFile("photos", octets.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /** Valeur attendue calculée hors du projet, en Python, d'après le format décrit par la classe. */
    @Test
    void ca18_lEmpreinteDUnContenuConnuEstStable() {
        assertThat(ContentFingerprint.of(valeurs(), List.of(photo("photo-a"))))
                .isEqualTo("65a3ed501cbc7a487cdbd15517b824c4f4d305d449c94ad821f7283991642408");
    }
}
