package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Règles d'alerte sans application : les valeurs attendues viennent des exemples du PRD. */
class AlertRulesTest {

    private static final String TPS = "123456789RT0001";
    private static final String TVQ = "1234567890TQ0001";
    private static final String TEXTE_PIECE = "Pièce à vérifier : nom de l'acheteur, description, conditions de paiement";

    private final AlertRules rules = new AlertRules(10_000L, 50_000L, 2L);

    private static Valeurs valeurs(Long avantTaxes, Long tps, Long tvq, long total, String numeroTps, String numeroTvq) {
        return new Valeurs(LocalDate.of(2026, 10, 11), "Fournisseur", null, "Fournitures", avantTaxes, tps, tvq, total, numeroTps, numeroTvq);
    }

    private List<AlerteCode> codes(Valeurs valeurs, Long lu) {
        return rules.evaluate(valeurs, lu, false).stream().map(Alerte::code).toList();
    }

    @ParameterizedTest
    @CsvSource({"9999, false", "10000, true"})
    void ca21_lesNumerosManquantsSontSignalesAPartirDe100Dollars(long total, boolean attendue) {
        assertThat(codes(valeurs(null, null, null, total, null, null), null).contains(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS)).isEqualTo(attendue);
    }

    @Test
    void ca22_seulLeNumeroManquantEstNomme() {
        assertThat(rules.evaluate(valeurs(null, null, null, 15_000L, TPS, null), null, false))
                .containsExactly(new Alerte(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, "Numéros d'inscription manquants : TVQ", null));
        assertThat(rules.evaluate(valeurs(null, null, null, 15_000L, null, TVQ), null, false))
                .containsExactly(new Alerte(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, "Numéros d'inscription manquants : TPS", null));
        assertThat(rules.evaluate(valeurs(null, null, null, 15_000L, null, null), null, false))
                .containsExactly(new Alerte(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, "Numéros d'inscription manquants : TPS, TVQ", null));
    }

    @Test
    void ca22_lesDeuxNumerosPresentsNeDonnentAucuneAlerte() {
        assertThat(rules.evaluate(valeurs(null, null, null, 15_000L, TPS, TVQ), null, false)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"49999, false", "50000, true"})
    void ca23_laPieceEstAVerifierAPartirDe500Dollars(long total, boolean attendue) {
        List<Alerte> alertes = rules.evaluate(valeurs(null, null, null, total, TPS, TVQ), null, false);

        assertThat(alertes).isEqualTo(attendue ? List.of(new Alerte(AlerteCode.PIECE_A_VERIFIER, TEXTE_PIECE, null)) : List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "10000, 10002, false",
        "10000, 10003, true",
        "10002, 10000, false",
        "10003, 10000, true",
    })
    void ca24_lEcartEstSignaleAuDelaDe2Cents(long avantTaxes, long total, boolean attendue) {
        assertThat(codes(valeurs(avantTaxes, null, null, total, TPS, TVQ), null).contains(AlerteCode.ECART_CALCUL)).isEqualTo(attendue);
    }

    @Test
    void ca25_lEcartHydroQuebecEstChiffre() {
        assertThat(rules.evaluate(valeurs(11_894L, 595L, 1_187L, 14_246L, TPS, TVQ), null, false))
                .containsExactly(new Alerte(AlerteCode.ECART_CALCUL, "Écart de calcul : 5,70 $", 570L));
    }

    @Test
    void ca25_lesCentimesDUnPetitEcartGardentDeuxChiffres() {
        assertThat(rules.evaluate(valeurs(1_000L, null, null, 1_005L, null, null), null, false))
                .containsExactly(new Alerte(AlerteCode.ECART_CALCUL, "Écart de calcul : 0,05 $", 5L));
    }

    @Test
    void ca26_sansMontantAvantTaxesLEcartNEstPasCalcule() {
        assertThat(codes(valeurs(null, 500L, 500L, 3_500L, null, null), null)).isEmpty();
    }

    @Test
    void ca26_uneTaxeVideCompteZeroDansLaSeuleComparaison() {
        Valeurs saisies = valeurs(10_000L, null, null, 11_500L, TPS, TVQ);

        assertThat(rules.evaluate(saisies, null, false)).containsExactly(new Alerte(AlerteCode.ECART_CALCUL, "Écart de calcul : 15,00 $", 1_500L));
        assertThat(saisies.tpsCents()).isNull();
        assertThat(saisies.tvqCents()).isNull();
    }

    @Test
    void ca29_leTotalLuPlusEleveQueLeTotalValideDeclencheLeSeuil() {
        assertThat(codes(valeurs(null, null, null, 4_000L, null, null), 12_000L)).containsExactly(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS);
        assertThat(codes(valeurs(null, null, null, 4_000L, TPS, TVQ), 50_000L)).containsExactly(AlerteCode.PIECE_A_VERIFIER);
    }

    @Test
    void ca29_unTotalLuPlusBasNeRetireRien() {
        assertThat(codes(valeurs(null, null, null, 12_000L, null, null), 4_000L)).containsExactly(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS);
    }

    @Test
    void ca27_leDoublonDonneSonTexteSansRienDireDeLAutreRecu() {
        assertThat(rules.evaluate(valeurs(null, null, null, 2_500L, null, null), null, true))
                .containsExactly(new Alerte(AlerteCode.DOUBLON_POSSIBLE, "Doublon possible", null));
    }

    @Test
    void ca11_lesAlertesSuiventLOrdreDuTableau() {
        assertThat(rules.evaluate(valeurs(50_000L, null, null, 60_000L, null, null), null, true)).extracting(Alerte::code)
                .containsExactly(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, AlerteCode.PIECE_A_VERIFIER, AlerteCode.ECART_CALCUL, AlerteCode.DOUBLON_POSSIBLE);
    }

    @Test
    void ca21_lesSeuilsViennentDeLaConfiguration() {
        AlertRules autres = new AlertRules(5_000L, 7_000L, 10L);

        assertThat(autres.evaluate(valeurs(1_000L, null, null, 5_000L, null, null), null, false)).extracting(Alerte::code)
                .containsExactly(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, AlerteCode.ECART_CALCUL);
        assertThat(autres.evaluate(valeurs(6_990L, null, null, 7_000L, TPS, TVQ), null, false)).extracting(Alerte::code)
                .containsExactly(AlerteCode.PIECE_A_VERIFIER);
    }
}
