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

    private final AlertRules rules = new AlertRules(10_000L, 50_000L, 2L, 5_000L, 9_975L, 2L);

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
        assertThat(codes(valeurs(null, 500L, 998L, 3_500L, null, null), null)).isEmpty();
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
        AlertRules autres = new AlertRules(5_000L, 7_000L, 10L, 5_000L, 9_975L, 2L);

        assertThat(autres.evaluate(valeurs(1_000L, null, null, 5_000L, null, null), null, false)).extracting(Alerte::code)
                .containsExactly(AlerteCode.NUMEROS_INSCRIPTION_MANQUANTS, AlerteCode.ECART_CALCUL);
        assertThat(autres.evaluate(valeurs(6_990L, null, null, 7_000L, TPS, TVQ), null, false)).extracting(Alerte::code)
                .containsExactly(AlerteCode.PIECE_A_VERIFIER);
    }

    private List<Alerte> taxes(AlertRules regles, Long avantTaxes, Long tps, Long tvq, long total) {
        return regles.evaluate(valeurs(avantTaxes, tps, tvq, total, TPS, TVQ), null, false).stream()
                .filter(a -> a.code() == AlerteCode.RAPPORT_TVQ_TPS || a.code() == AlerteCode.TPS_AU_DELA_DU_TAUX).toList();
    }

    private List<Alerte> taxes(Long avantTaxes, Long tps, Long tvq, long total) {
        return taxes(rules, avantTaxes, tps, tvq, total);
    }

    private static Alerte rapport(String tvq, String tps) {
        return new Alerte(AlerteCode.RAPPORT_TVQ_TPS, "Rapport TVQ / TPS inhabituel : TVQ " + tvq + " pour une TPS de " + tps, null);
    }

    private static Alerte auDela(String tps, String avantTaxes) {
        return new Alerte(AlerteCode.TPS_AU_DELA_DU_TAUX, "TPS supérieure à 5 % du montant avant taxes : " + tps + " pour " + avantTaxes, null);
    }

    @Test
    void ca104_lesReceptsCoherentsNOntAucuneAlerteDeTaxes() {
        assertThat(taxes(11_894L, 595L, 1_187L, 14_246L)).as("Hydro-Québec").isEmpty();
        assertThat(taxes(61_827L, 3_091L, 6_167L, 71_085L)).as("Garage Iberville").isEmpty();
    }

    @Test
    void ca105_laTpsEtLaTvqInverseesLevantLesDeuxAlertes() {
        assertThat(taxes(11_894L, 1_187L, 595L, 13_676L)).containsExactly(rapport("5,95 $", "11,87 $"), auDela("11,87 $", "118,94 $"));
    }

    @Test
    void ca106_uneTvqMalLueLeveLeRapportSeulement() {
        List<Alerte> alertes = rules.evaluate(valeurs(61_827L, 3_091L, 1_667L, 66_585L, TPS, TVQ), null, false);

        assertThat(alertes).extracting(Alerte::code).containsExactly(AlerteCode.PIECE_A_VERIFIER, AlerteCode.RAPPORT_TVQ_TPS);
        assertThat(alertes.get(1).message()).isEqualTo("Rapport TVQ / TPS inhabituel : TVQ 16,67 $ pour une TPS de 30,91 $");
    }

    @Test
    void ca107_uneTpsA525PourCentLeveSeulementLePlafond() {
        assertThat(taxes(40_000L, 2_100L, 4_190L, 46_290L)).containsExactly(auDela("21,00 $", "400,00 $"));
    }

    @Test
    void ca108_unPanierMixteSousLeTauxNAlertePas() {
        assertThat(taxes(10_000L, 300L, 599L, 10_899L)).isEmpty();
    }

    @Test
    void ca109_uneTaxeVideOuANulleNEstPasControlee() {
        assertThat(taxes(11_894L, null, 1_187L, 13_081L)).isEmpty();
        assertThat(taxes(11_894L, 595L, null, 12_489L)).isEmpty();
        assertThat(taxes(11_894L, 0L, 1_187L, 13_081L)).isEmpty();
        assertThat(taxes(11_894L, 595L, 0L, 12_489L)).isEmpty();
        assertThat(taxes(null, null, null, 0L)).isEmpty();
        assertThat(taxes(null, 1_187L, 595L, 13_676L)).containsExactly(rapport("5,95 $", "11,87 $"));
    }

    @ParameterizedTest
    @CsvSource({"1997, false", "1998, true", "1993, false", "1992, true"})
    void ca110_lEcartDuRapportSeMesureExactementALaTolerance(long tvq, boolean attendue) {
        assertThat(taxes(null, 1_000L, tvq, 1_000L + tvq).isEmpty()).isEqualTo(!attendue);
    }

    @ParameterizedTest
    @CsvSource({"2002, '20,02 $', false", "2003, '20,03 $', true"})
    void ca110_lePlafondDeTpsSeMesureExactementALaTolerance(long tps, String texte, boolean attendue) {
        assertThat(taxes(40_000L, tps, null, 40_000L + tps)).isEqualTo(attendue ? List.of(auDela(texte, "400,00 $")) : List.of());
    }

    @Test
    void ca113_lesTauxEtLaToleranceViennentDeLaConfiguration() {
        AlertRules taux = new AlertRules(10_000L, 50_000L, 2L, 6_000L, 9_000L, 2L);
        assertThat(taxes(taux, 10_000L, 600L, 900L, 11_500L)).isEmpty();
        assertThat(taxes(10_000L, 600L, 900L, 11_500L)).extracting(Alerte::code)
                .containsExactly(AlerteCode.RAPPORT_TVQ_TPS, AlerteCode.TPS_AU_DELA_DU_TAUX);

        AlertRules tolerante = new AlertRules(10_000L, 50_000L, 2L, 5_000L, 9_975L, 5L);
        assertThat(taxes(tolerante, null, 1_000L, 1_998L, 2_998L)).isEmpty();
        assertThat(taxes(null, 1_000L, 1_998L, 2_998L)).extracting(Alerte::code).containsExactly(AlerteCode.RAPPORT_TVQ_TPS);
    }
}
