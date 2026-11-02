package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** Une configuration modifiée suffit à changer les règles : la liste n'est pas une constante. */
@RealServerTest
@TestPropertySource(properties = {"recus.types-depense[0]=Bois de chauffage", "recus.types-depense[1]=Déneigement"})
class RecuConfiguredRulesRealServerTest {

    @LocalServerPort
    int port;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private Reponse valider(String champ, Object valeur) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("typeDepense", "Déneigement");
        valeurs.put(champ, valeur);
        return RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
    }

    @Test
    void ca15_laListeModifieeEstSuivie() {
        assertThat(valider("typeDepense", "Bois de chauffage").statut()).isEqualTo(201);
        assertThat(valider("typeDepense", "Déneigement").statut()).isEqualTo(201);

        Reponse refuse = valider("typeDepense", "Fournitures");

        assertThat(refuse.statut()).isEqualTo(400);
        assertThat(refuse.json().get("champs")).isEqualTo(List.of(Map.of("champ", "typeDepense", "code", "HORS_LISTE")));
    }

}
