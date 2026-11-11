package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.service.GoogleTokenService;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Le dépôt en tâche de fond, sur serveur réel : un fil, réveillé par la validation, par l'autorisation et par l'heure d'un nouvel essai. Drive et le Sheet sont en mémoire,
 * Google (les jetons) est un serveur local. Le passage périodique est repoussé à une heure : seuls les réveils agissent.
 */
@RealServerTest
@TestPropertySource(properties = {"recus.copie.automatique=true", "recus.copie.intervalle=1h"})
class RecuCopyAutomaticRealServerTest {

    private static final GoogleStub GOOGLE = demarrerGoogle();

    private static GoogleStub demarrerGoogle() {
        try {
            return new GoogleStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void adressesDeGoogle(DynamicPropertyRegistry registre) {
        registre.add("recus.google.adresse-consentement", GOOGLE::adresseConsentement);
        registre.add("recus.google.adresse-jeton", GOOGLE::adresseJeton);
        registre.add("recus.google.delai-appel", () -> "3s");
    }

    @AfterAll
    static void arreterGoogle() {
        GOOGLE.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    InMemoryGoogle google;

    @Autowired
    GoogleTokenService jetons;

    private TestClient client;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        GOOGLE.reinitialiser();
        google.reset();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private Reponse valider() {
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png());
        assertThat(reponse.statut()).isEqualTo(201);
        return reponse;
    }

    /** Une photo est déposée quand son identifiant Drive est connu (D-66 : plus d'état de copie). */
    private boolean deposee(Object recuId) {
        return base.queryForObject("select count(*) from photo where recu_id = ? and drive_file_id is not null", Long.class, recuId) == 1;
    }

    /** Le fil de la copie est un autre fil que le test : on attend ce qu'il doit finir. */
    private static void attendre(String quoi, BooleanSupplier condition) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as(quoi).isLessThan(limite);
            Thread.sleep(25);
        }
    }

    /** Suit la demande d'autorisation et revient par le rappel, comme le navigateur après le consentement. */
    private void autoriser() {
        String demande = client.get("/api/google/autorisation").enTete("Location");
        String state = URLDecoder.decode(UriComponentsBuilder.fromUri(URI.create(demande)).build(true).getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
        Reponse reponse = client.get("/api/google/rappel?code=code-temoin&state=" + state);
        String suite = reponse.enTete("Location");
        if (suite.startsWith("http://localhost:" + port + "/api/google/rappel")) {
            client.get(suite.substring(("http://localhost:" + port).length()));
        }
        assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
    }

    private void autorisationDirecte() {
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
    }

    @Test
    void ca45_laReponseDeValidationNAPasAttenduLeDepotQuiSeFaitEnTacheDeFondSansAppelAuSheet() throws Exception {
        autorisationDirecte();
        CountDownLatch depotCommence = new CountDownLatch(1);
        CountDownLatch liberation = new CountDownLatch(1);
        google.beforeEachDeposit(() -> {
            depotCommence.countDown();
            try {
                liberation.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Reponse reponse = valider();

        assertThat(liberation.getCount()).as("le dépôt n'est pas fini quand la validation répond").isEqualTo(1);
        assertThat(reponse.json()).doesNotContainKeys("etatCopie", "motifBlocage", "prochaineTentative");
        assertThat(depotCommence.await(10, TimeUnit.SECONDS)).as("la validation réveille le fil de dépôt").isTrue();
        assertThat(deposee(reponse.json().get("id"))).isFalse();
        liberation.countDown();
        attendre("photo déposée", () -> deposee(reponse.json().get("id")));
        assertThat(google.journal()).containsExactly("drive:" + base.queryForObject("select id from photo", String.class));
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca65_uneNouvelleAutorisationReprendAussitotLesDepotsEnAttenteEtLesSuivantsNeDemandentRien() throws Exception {
        Object enAttente = valider().json().get("id");
        assertThat(deposee(enAttente)).isFalse();

        autoriser();

        attendre("photo reprise et déposée", () -> deposee(enAttente));
        Object suivant = valider().json().get("id");
        attendre("photo suivante déposée", () -> deposee(suivant));
        assertThat(google.files()).hasSize(2);
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca65_uneNouvelleAutorisationRetenteAussitotUnePhotoEnPanneSansAttendreLEssaiProgramme() throws Exception {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        google.failNextDeposit(new TransientGoogleException("PANNE"));
        Object enPanne = valider().json().get("id");
        attendre("dépôt tenté et en panne", () -> google.depositCalls() == 1);
        assertThat(deposee(enPanne)).isFalse();

        autoriser();

        attendre("photo retentée et déposée", () -> deposee(enPanne));
        assertThat(google.depositCalls()).isEqualTo(2);
    }

    @Test
    void ca66_apresUneNouvelleAutorisationLesPhotosSontDeposeesSansDoublon() throws Exception {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        google.failNextDeposit(new TransientGoogleException("PANNE"));
        Object enAttente = valider().json().get("id");
        attendre("dépôt tenté et en panne", () -> google.depositCalls() == 1);
        GOOGLE.repondre(400, "{\"error\":\"invalid_grant\"}");

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"A_RENOUVELER\"}");
        Object pendant = valider().json().get("id");
        assertThat(deposee(enAttente) || deposee(pendant)).isFalse();
        GOOGLE.reinitialiser();
        autoriser();

        attendre("photos déposées", () -> deposee(enAttente) && deposee(pendant));
        assertThat(google.files()).hasSize(2);
        assertThat(google.depositCalls()).isEqualTo(3);
        assertThat(google.writeCalls()).isZero();
    }
}
