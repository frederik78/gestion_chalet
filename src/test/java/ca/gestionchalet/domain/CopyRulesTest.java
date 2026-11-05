package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** D-62 : la copie vers le Sheet n'est configurée qu'avec un fichier ET un onglet, tous deux non vides (espace insécable compris). */
class CopyRulesTest {

    static Stream<String> absents() {
        return Stream.of(null, "", " ", "  \t", " ", "   ", " ");
    }

    @Test
    void d62_unFichierEtUnOngletRenseignesSontConfigures() {
        assertThat(new CopyRules("fichier-essai", "Comptes 2026").sheetConfigured()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("absents")
    void d62_unFichierAbsentVideOuDeSeparateursNeSuffitPas(String vide) {
        assertThat(new CopyRules(vide, "Comptes 2026").sheetConfigured()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("absents")
    void d62_unOngletAbsentVideOuDeSeparateursNeSuffitPas(String vide) {
        assertThat(new CopyRules("fichier-essai", vide).sheetConfigured()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("absents")
    void d62_ni_l_un_ni_l_autre_n_est_configure(String vide) {
        assertThat(new CopyRules(vide, vide).sheetConfigured()).isFalse();
    }
}
