package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** CA113 : les taux et la tolérance du contrôle des taxes sont ceux de la configuration, aucun n'est figé dans le code. */
@RealServerTest
@TestPropertySource(properties = {"recus.alertes.taux-tps-millipourcent=6000", "recus.alertes.taux-tvq-millipourcent=9000",
    "recus.alertes.ecart-taxes-tolere-cents=5"})
class RecuTaxCheckConfiguredRealServerTest {

    @LocalServerPort
    int port;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private Reponse valider(Long avantTaxes, Long tps, Long tvq, long total) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", "Configuré " + UUID.randomUUID());
        valeurs.put("montantAvantTaxesCents", avantTaxes);
        valeurs.put("tpsCents", tps);
        valeurs.put("tvqCents", tvq);
        valeurs.put("montantTotalCents", total);
        valeurs.put("numeroInscriptionTps", "123456789RT0001");
        valeurs.put("numeroInscriptionTvq", "1234567890TQ0001");
        return RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
    }

    @Test
    void ca113_lesTauxDeLaConfigurationRemplacentLesTauxParDefaut() {
        Reponse reponse = valider(10_000L, 600L, 900L, 11_500L);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("alertes", List.of());
    }

    @Test
    void ca113_laToleranceDeLaConfigurationRemplaceLaToleranceParDefaut() {
        assertThat(valider(null, 600L, 905L, 1_505L).json()).containsEntry("alertes", List.of());
        assertThat(valider(null, 600L, 906L, 1_506L).json().get("alertes")).asList().hasSize(1);
    }
}
