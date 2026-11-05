package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** D-61 : les règles de « texte vide » et de comparaison d'un fournisseur (R-15), valeurs attendues écrites à la main. */
class ReceiptTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", " ", "   ", " ", " ", "　", "  "})
    void d61_unTexteFaitSeulementDeSeparateursEstVide(String text) {
        assertThat(ReceiptText.isEmpty(text)).isTrue();
        assertThat(ReceiptText.normalize(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", " a", "a b", ".", "0", "ㅤ", "⠀"})
    void d61_unTexteAvecUnSigneNEstPasVide(String text) {
        assertThat(ReceiptText.isEmpty(text)).isFalse();
        assertThat(ReceiptText.normalize(text)).isNotEmpty();
    }

    @Test
    void d61_normaliserRetireLesEspacesOrdinairesEnBordureEtGardeLeResteTelQuel() {
        assertThat(ReceiptText.normalize("  L’Épicerie  Inc.  ")).isEqualTo("L’Épicerie  Inc.");
        assertThat(ReceiptText.normalize(" Café")).isEqualTo(" Café");
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
        "Café| Café", "Café |Café", "　Café  |  Café"})
    void d61_ca27_lesFournisseursSontLesMemesSansTenirCompteDeLaCasseDesAccentsDesLigaturesDeLApostropheNiDesSeparateursEnBordure(
            String first, String second) {
        assertThat(ReceiptText.sameSupplier(first, second)).isTrue();
        assertThat(ReceiptText.sameSupplier(second, first)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} / {1}")
    @CsvSource(delimiter = '|', value = {
        "Épicerie Inc.|Épicerie Inc", "Épicerie|Epicerie Inc", "Café du Lac|Café du Lac bis",
        "Café du Lac|Café du Lac", "Café du Lac|Cafédu Lac", "Café-du-Lac|Café du Lac",
        "L'Épicerie|L Épicerie", "L‘Épicerie|L'Épicerie", "Œ|O", "Æ|A", "Coeur|Cur"})
    void d61_ca27_rienDAutreNEstFusionne(String first, String second) {
        assertThat(ReceiptText.sameSupplier(first, second)).isFalse();
    }
}
