package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** La correction d'un reçu validé, par {@code PUT /api/recus/{id}} : les reçus sont validés par l'API, comme le fait la PWA, et la base est lue pour constater ce qui change. */
@RealServerTest
class RecuCorrectionRealServerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    InMemoryGoogle google;

    private TestClient client;

    @BeforeEach
    void connecter() {
        google.reset();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private static Map<String, Object> valeurs(String fournisseur, Long avantTaxes, Long tps, Long tvq, long total) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("date", "2026-06-16");
        valeurs.put("fournisseur", fournisseur);
        valeurs.put("typeDepense", "Fournitures");
        valeurs.put("montantAvantTaxesCents", avantTaxes);
        valeurs.put("tpsCents", tps);
        valeurs.put("tvqCents", tvq);
        valeurs.put("montantTotalCents", total);
        return valeurs;
    }

    private String valider(Map<String, Object> valeurs) {
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
        assertThat(reponse.statut()).isEqualTo(201);
        return (String) reponse.json().get("id");
    }

    private Reponse corriger(String id, Object version, Map<String, Object> valeurs) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("version", version);
        corps.put("valeurs", valeurs);
        return client.appelAvecJeton("PUT", "/api/recus/" + id, JSON.writeValueAsString(corps));
    }

    private Map<String, Object> ligne(String id) {
        return base.queryForMap("select * from recu where id = ?", id);
    }

    @Test
    void ca61_uneVersionPerimeeDonne409VersionPerimeeEtRienNeChange() {
        String id = valider(valeurs("Avant", null, null, null, 3500));
        assertThat(corriger(id, 1, valeurs("Deuxième", null, null, null, 3500)).statut()).isEqualTo(200);
        assertThat(corriger(id, 2, valeurs("Troisième", null, null, null, 3500)).statut()).isEqualTo(200);
        Map<String, Object> avant = ligne(id);

        Reponse reponse = corriger(id, 2, valeurs("Périmée", null, null, null, 9900));

        assertThat(reponse.statut()).isEqualTo(409);
        assertThat(reponse.json()).containsEntry("code", "VERSION_PERIMEE");
        assertThat(avant).containsEntry("version", 3).containsEntry("fournisseur", "Troisième");
        assertThat(ligne(id)).isEqualTo(avant);
    }
}
