package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.gestionchalet.domain.Valeurs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ContentFingerprintTest {

    private static Valeurs valeurs() {
        return new Valeurs(LocalDate.of(2026, 10, 11), "Fournisseur", "Objet", "Fournitures", 1000L, 50L, 100L, 1150L, "123", "456");
    }

    private static MultipartFile photo(String octets) {
        return new MockMultipartFile("photos", octets.getBytes(StandardCharsets.US_ASCII));
    }

    /** Valeur attendue calculée hors du projet, en Python, d'après le format décrit par la classe. */
    @Test
    void ca18_lEmpreinteDUnContenuConnuEstStable() {
        assertThat(ContentFingerprint.of(valeurs(), List.of(photo("photo-a"))))
                .isEqualTo("65a3ed501cbc7a487cdbd15517b824c4f4d305d449c94ad821f7283991642408");
    }

    private static Valeurs avec(String fournisseur, String objet, Long tps) {
        return new Valeurs(LocalDate.of(2026, 10, 11), fournisseur, objet, "Fournitures", 1000L, tps, 100L, 1150L, "123", "456");
    }

    private static String empreinte(Valeurs valeurs, String... photos) {
        return ContentFingerprint.of(valeurs, Stream.of(photos).map(ContentFingerprintTest::photo).toList());
    }

    @Test
    void ca18_desOctetsDeplacesDUnChampAuSuivantChangentLEmpreinte() {
        assertThat(empreinte(avec("ab", "c", 50L), "a")).isNotEqualTo(empreinte(avec("a", "bc", 50L), "a"));
    }

    @Test
    void ca18_unTexteAbsentDiffereDUnTexteVide() {
        assertThat(empreinte(avec("F", null, 50L), "a")).isNotEqualTo(empreinte(avec("F", "", 50L), "a"));
    }

    @Test
    void ca18_uneTaxeVideDiffereDeZero() {
        assertThat(empreinte(avec("F", "O", null), "a")).isNotEqualTo(empreinte(avec("F", "O", 0L), "a"));
    }

    @Test
    void ca18_lOrdreEtLeNombreDesPhotosChangentLEmpreinte() {
        Valeurs valeurs = avec("F", "O", 50L);
        assertThat(empreinte(valeurs, "a", "b"))
                .isEqualTo(empreinte(valeurs, "a", "b"))
                .isNotEqualTo(empreinte(valeurs, "b", "a"))
                .isNotEqualTo(empreinte(valeurs, "a"));
    }

    @Test
    void ca19_unEchecDeLectureDUnePhotoEstRemonte() throws IOException {
        MultipartFile illisible = mock(MultipartFile.class);
        IOException panne = new IOException("panne");
        when(illisible.getInputStream()).thenThrow(panne);

        assertThatThrownBy(() -> ContentFingerprint.of(valeurs(), List.of(illisible)))
                .isInstanceOf(UncheckedIOException.class)
                .hasCause(panne);
    }
}
