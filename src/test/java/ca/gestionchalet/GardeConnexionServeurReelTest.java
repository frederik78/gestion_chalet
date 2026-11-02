package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ca.gestionchalet.support.ClientBrut;
import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Ce qui protège la connexion avant bcrypt : compte des tentatives, forme de la requête, corps jamais lu quand il est refusé. */
@ServeurReelTest
@ExtendWith(OutputCaptureExtension.class)
class GardeConnexionServeurReelTest {

    private static final int ECHECS_PAR_DEFAUT = 5;
    private static final String TEMOIN = "temoin-jamais-journalise";

    @LocalServerPort
    int port;

    @MockitoSpyBean
    PasswordEncoder encodeur;

    private ClientEssai client() {
        ClientEssai client = new ClientEssai(port, "source-" + UUID.randomUUID());
        client.get("/actuator/health");
        return client;
    }

    @Test
    void cs05_deuxCentsRequetesSimultaneesDonnentAuPlusCinqComparaisons() throws Exception {
        int simultanees = 200;
        ClientEssai client = client();
        clearInvocations(encodeur);
        List<HttpRequest> requetes = new ArrayList<>();
        for (int i = 0; i < simultanees; i++) {
            requetes.add(client.requeteConnexion("faux").build());
        }
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> statuts = new ArrayList<>();
        Map<Integer, Integer> parStatut = new TreeMap<>();
        try (ExecutorService fils = Executors.newFixedThreadPool(simultanees)) {
            for (HttpRequest requete : requetes) {
                statuts.add(fils.submit(() -> {
                    depart.await();
                    return client.envoyer(requete).statut();
                }));
            }
            depart.countDown();
            for (Future<Integer> statut : statuts) {
                parStatut.merge(statut.get(), 1, Integer::sum);
            }
        }

        assertThat(parStatut).isEqualTo(Map.of(401, ECHECS_PAR_DEFAUT, 429, simultanees - ECHECS_PAR_DEFAUT));
        verify(encodeur, times(ECHECS_PAR_DEFAUT)).matches(any(), anyString());
    }

    @Test
    void cs05_uneSourceBloqueeNEstPasComparee() {
        ClientEssai client = client();
        for (int essai = 0; essai < ECHECS_PAR_DEFAUT; essai++) {
            client.connecter("faux");
        }
        clearInvocations(encodeur);

        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(429);

        verify(encodeur, never()).matches(any(), anyString());
    }

    @Test
    void cs04_unMotDePasseDansLUrlEstRefuseSansEffet() {
        ClientEssai client = client();
        clearInvocations(encodeur);

        Reponse reponse = client.envoyer(client.requete("/api/connexion?motDePasse=" + ConfigurationEssai.MOT_DE_PASSE)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"));
        assertThat(client.cookie("JSESSIONID")).isNull();
        verify(encodeur, never()).matches(any(), anyString());
    }

    @Test
    void cs04_leMotDePasseNApparaitDansAucunJournal(CapturedOutput journal) {
        ClientEssai client = client();

        client.connecter(TEMOIN);
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(journal.getAll()).doesNotContain(TEMOIN, ConfigurationEssai.MOT_DE_PASSE);
    }

    /** Juste au-delà de la borne de 64 Ko, et bien au-delà : le corps n'arrive jamais, le refus précède la lecture et bcrypt. */
    @ParameterizedTest
    @ValueSource(longs = {65_537L, 400_000_000L})
    void cs08_unFormulaireAnnonceAuDelaDeLaBorneEstRefuseSansEtreLu(long longueur) {
        String jeton = UUID.randomUUID().toString();
        clearInvocations(encodeur);

        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "POST /api/connexion HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + longueur + "\r\n"
                + "X-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n\r\nmotDePasse=", 5_000);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
        verify(encodeur, never()).matches(any(), anyString());
    }

    /** Un refus de taille ne compte aucune tentative : sinon, envoyer de gros formulaires bloquerait la source. */
    @Test
    void cs05_unFormulaireTropGrosNeCompteAucuneTentative() {
        ClientEssai client = client();
        for (int essai = 0; essai <= ECHECS_PAR_DEFAUT; essai++) {
            Reponse reponse = client.envoyer(client.requeteConnexion("x")
                    .POST(HttpRequest.BodyPublishers.ofString("motDePasse=" + "x".repeat(70_000))).build());
            assertThat(reponse.statut()).as("envoi n° %d", essai + 1).isEqualTo(413);
        }

        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    /** Le corps annoncé n'arrive jamais : un refus qui tarderait trahirait une lecture. Refusé avant le compte des tentatives et avant bcrypt. */
    @ParameterizedTest
    @ValueSource(strings = {"multipart/form-data; boundary=x", "MULTIPART/FORM-DATA; boundary=x", "Multipart/Mixed; boundary=x", "text/plain", "*/*"})
    void cs08_unAutreFormatQuUnFormulaireEstRefuseSansEtreLu(String type) {
        String jeton = UUID.randomUUID().toString();
        clearInvocations(encodeur);

        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "POST /api/connexion HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: 56000000\r\n"
                + "X-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n\r\n--x\r\n", 5_000);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
        verify(encodeur, never()).matches(any(), anyString());
    }
}
