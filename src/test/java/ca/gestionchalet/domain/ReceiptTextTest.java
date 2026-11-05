package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** D-61 : les règles de « texte vide » et de comparaison d'un fournisseur (R-15), valeurs attendues écrites à la main. */
class ReceiptTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\u00A0", "\u00A0 \u00A0", "\u202F", "\u2007", "\u3000", "\u2003\u00A0"})
    void d61_unTexteFaitSeulementDeSeparateursEstVide(String text) {
        assertThat(ReceiptText.isEmpty(text)).isTrue();
        assertThat(ReceiptText.normalize(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "\u00A0a", "a\u00A0b", ".", "0", "\u3164", "\u2800"})
    void d61_unTexteAvecUnSigneNEstPasVide(String text) {
        assertThat(ReceiptText.isEmpty(text)).isFalse();
        assertThat(ReceiptText.normalize(text)).isNotEmpty();
    }

    @Test
    void d61_normaliserRetireLesEspacesOrdinairesEnBordureEtGardeLeResteTelQuel() {
        assertThat(ReceiptText.normalize("  L’Épicerie \u00A0Inc.  ")).isEqualTo("L’Épicerie \u00A0Inc.");
        assertThat(ReceiptText.normalize("\u00A0Café")).isEqualTo("\u00A0Café");
    }

    @Test
    void d61_unTexteAbsentEstVide() {
        assertThat(ReceiptText.isEmpty(null)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} = {1}")
    @CsvSource(delimiter = '|', value = {
        "Café du Lac|CAFE DU LAC", "Épicerie Noël|epicerie noel",
        "Cœur|Coeur", "CŒUR|coeur", "Coeur|CŒUR", "Æther|Aether", "æther|AETHER", "Aether|ÆTHER",
        "L’Épicerie|L'Epicerie", "L'Epicerie|L’EPICERIE",
        "Café|\u00A0Café", "Café\u202F|Café", "\u3000Café\u00A0 |  Café"})
    void d61_ca27_lesFournisseursSontLesMemesSansTenirCompteDeLaCasseDesAccentsDesLigaturesDeLApostropheNiDesSeparateursEnBordure(
            String first, String second) {
        assertThat(ReceiptText.sameSupplier(first, second)).isTrue();
        assertThat(ReceiptText.sameSupplier(second, first)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} / {1}")
    @CsvSource(delimiter = '|', value = {
        "Épicerie Inc.|Épicerie Inc", "Épicerie|Epicerie Inc", "Café du Lac|Café du Lac bis",
        "Café\u00A0du Lac|Café du Lac", "Café du Lac|Cafédu Lac", "Café-du-Lac|Café du Lac",
        "L'Épicerie|L Épicerie", "L‘Épicerie|L'Épicerie", "Œ|O", "Æ|A", "Coeur|Cur"})
    void d61_ca27_rienDAutreNEstFusionne(String first, String second) {
        assertThat(ReceiptText.sameSupplier(first, second)).isFalse();
    }

    /** Une longue suite de séparateurs au milieu ne doit pas coûter un temps quadratique (80 000 caractères : 28 s mesurés avec l'ancienne expression). */
    @Test
    void d61_laComparaisonEstLineaireMemeAvecUneLongueSuiteDeSeparateurs() {
        String longue = "a" + " ".repeat(200_000) + "b";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(ReceiptText.sameSupplier(longue, longue)).isTrue();
            assertThat(ReceiptText.sameSupplier(longue, "a b")).isFalse();
        });
    }

    @Test
    void d61_lesSeparateursEnBordureSontRetiresDesDeuxCotesMemeSiLeTexteNEnAQueDeLaMatiere() {
        assertThat(ReceiptText.sameSupplier("\u00A0\t \u3000a\u2003\n", "A")).isTrue();
    }
}
