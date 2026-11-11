package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * D-18, D-62 : le fichier et l'onglet se déduisent de l'année du reçu ; une entrée sans fichier ou sans onglet (espace insécable compris) n'est pas une entrée, donc jamais
 * ouverte.
 */
class CopyRulesTest {

    static Stream<String> absents() {
        return Stream.of(null, "", " ", "  \t", " ", "   ", " ");
    }

    private static final SheetTarget COMPTES_2026 = new SheetTarget("fichier-a", "Comptes 2026");

    @Test
    void ca31_lAnneeConfigureeDonneSonFichierEtSonOngletEtAucunAutre() {
        CopyRules rules = new CopyRules(Map.of(2026, COMPTES_2026, 2025, new SheetTarget("fichier-b", "Comptes 2025")));

        assertThat(rules.sheetFor(2026)).contains(COMPTES_2026);
        assertThat(rules.sheetFor(2025)).contains(new SheetTarget("fichier-b", "Comptes 2025"));
        assertThat(rules.sheetFor(2027)).isEmpty();
    }

    @Test
    void ca32_d18_uneAnneeAbsenteDeLaCorrespondanceNaPasDeFichierMaisLaCorrespondanceReste() {
        CopyRules rules = new CopyRules(Map.of(2026, COMPTES_2026));

        assertThat(rules.anySheetConfigured()).isTrue();
        assertThat(rules.sheetFor(2030)).isEmpty();
    }

    @Test
    void d62_sansAucuneEntreeRienNEstConfigure() {
        assertThat(new CopyRules(Map.of()).anySheetConfigured()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("absents")
    void d62_unFichierAbsentVideOuDeSeparateursNEstPasUneEntree(String vide) {
        CopyRules rules = new CopyRules(Map.of(2026, new SheetTarget(vide, "Comptes 2026")));

        assertThat(rules.anySheetConfigured()).isFalse();
        assertThat(rules.sheetFor(2026)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("absents")
    void d62_unOngletAbsentVideOuDeSeparateursNEstPasUneEntree(String vide) {
        CopyRules rules = new CopyRules(Map.of(2026, new SheetTarget("fichier-a", vide)));

        assertThat(rules.anySheetConfigured()).isFalse();
        assertThat(rules.sheetFor(2026)).isEmpty();
    }

    @Test
    void d62_uneEntreeInutilisableNeFaitPasPerdreLesAutres() {
        CopyRules rules = new CopyRules(Map.of(2025, new SheetTarget("fichier-b", " "), 2026, COMPTES_2026));

        assertThat(rules.sheetFor(2025)).isEmpty();
        assertThat(rules.sheetFor(2026)).contains(COMPTES_2026);
    }
}
